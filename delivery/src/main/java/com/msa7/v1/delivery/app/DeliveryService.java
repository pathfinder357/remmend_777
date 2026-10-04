package com.msa7.v1.delivery.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa7.v1.delivery.domain.aggregateDelivery.Delivery;
import com.msa7.v1.delivery.domain.aggregateDelivery.DeliveryRouteRecord;
import com.msa7.v1.delivery.domain.repo.DeliveryRepo;
import com.msa7.v1.delivery.domain.vo.DeliveryStatus;
import com.msa7.v1.delivery.domain.vo.ManagerType;
import com.msa7.v1.delivery.domain.vo.RouteStatus;
import com.msa7.v1.delivery.infra.entity.ProcessedEventEntity;
import com.msa7.v1.delivery.infra.feign.HubClient;
import com.msa7.v1.delivery.infra.outobx.DeliveryOutboxEvent;
import com.msa7.v1.delivery.infra.outobx.DeliveryOutboxEventRepo;
import com.msa7.v1.delivery.infra.repo.JpaDeliveryRouteRecordRepository;
import com.msa7.v1.delivery.infra.repo.ProcessedEventRepository;
import com.msa7.v1.delivery.presentation.dto.HubRouteResponse;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryFailedEvent;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryResponse;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryRouteResponse;
import com.msa7.v1.delivery.presentation.dto.payload.OrderCreatedEvent;
import com.msa7.v1.delivery.presentation.internal.dto.DeliveryRouteInfoResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

	private final DeliveryRepo deliveryRepo;
	private final HubClient hubClient;
	private final JpaDeliveryRouteRecordRepository routeRepo;
	private final DeliveryOutboxEventRepo outboxEventRepo;
	private final ProcessedEventRepository processedEventRepo;
	private final DeliveryManagerService deliveryManagerService;
	private final ObjectMapper objectMapper;

	// 주믄한 사람이 배송을 확인하고 싶을때
	@Transactional
	public void updateDeliveryStatus(UUID deliveryId, DeliveryStatus status) {
		Delivery delivery = deliveryRepo.findById(deliveryId)
			.orElseThrow(() -> new IllegalArgumentException("배송을 찾을 수 없습니다."));

		delivery.updateStatus(status);
		deliveryRepo.save(delivery);
	}

	/*
	 * 배송기사가 배송상태를 변경할때의 메서드.
	 * requesterId가 null이 아니면 본인에게 배정된 경로만 변경할 수 있다.
	 */
	@Transactional
	public void updateDeliveryRouteStatus(UUID deliveryId, UUID routeId, RouteStatus newStatus, UUID requesterId) {
		// 1. 배송 엔티티 조회
		Delivery delivery = deliveryRepo.findById(deliveryId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 배송입니다."));

		delivery.updateRouteStatus(routeId, newStatus, requesterId);

		// 영속화
		// 전체 완료 시 내부적으로 DeliveryCompletedEvent가 Outbox에 자동 적재됨
		deliveryRepo.save(delivery);
	}


	@Transactional
	public void deleteDelivery(UUID id, UUID deletedBy) {
		deliveryRepo.deleteById(id, deletedBy);
	}

	// 요청사항
	@Transactional(readOnly = true)
	public List<DeliveryRouteResponse> getDeliveryRoutes(UUID hubId, RouteStatus status) {
		return routeRepo.findAllByStartHubIdAndStatusAndDeletedAtIsNull(hubId, status).stream()
			.map(route -> new DeliveryRouteResponse(
				route.getId(),
				route.getSequence(),
				route.getStartHubId(),
				route.getEndHubId(),
				route.getStatus()
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public DeliveryResponse getDeliveryInfo(UUID deliveryId) {
		Delivery delivery = deliveryRepo.findById(deliveryId)
			.orElseThrow(() -> new IllegalArgumentException("배송을 찾을 수 없습니다."));

		return new DeliveryResponse(
			delivery.getId(),
			delivery.getOrderId(),
			delivery.getStatus()
		);
	}

	/*
	 * 주문 생성 이벤트를 받아 배송을 만든다.
	 *
	 * RabbitMQ는 at-least-once이므로 같은 이벤트가 두 번 올 수 있다.
	 * 처리 이력(eventId)과 주문당 배송 존재 여부를 먼저 확인해 중복 생성을 막고,
	 * 최종 방어선으로 p_delivery.order_id에 UNIQUE 제약을 둔다.
	 */
	@Transactional
	public void createDeliveryFromOrder(OrderCreatedEvent event, UUID eventId) {
		if (eventId != null && processedEventRepo.existsById(eventId)) {
			log.info("이미 처리한 이벤트입니다. eventId={}", eventId);
			return;
		}

		if (deliveryRepo.findByOrderId(event.orderId()).isPresent()) {
			log.info("이미 배송이 생성된 주문입니다. orderId={}", event.orderId());
			markProcessed(eventId);
			return;
		}

		try {
			// 업체 배송 담당자를 순번대로 배정
			UUID companyManagerId =
				deliveryManagerService.assignNextManager(event.endHubId(), ManagerType.COMPANY_STAFF);

			// 도메인 객체 생성
			Delivery delivery = Delivery.createFromOrder(
				event.orderId(),
				event.startHubId(),
				event.endHubId(),
				event.destinationAddress(),
				event.receiverSlackId(),
				companyManagerId
			);

			// 허브 간 경로(간선) 조회 및 기록 생성
			HubRouteResponse hubRoute = hubClient.getRouteInfo(event.startHubId(), event.endHubId());
			List<HubRouteResponse> hubRoutes = List.of(hubRoute);
			List<DeliveryRouteRecord> routes = new ArrayList<>();

			int sequence = 0;
			for (HubRouteResponse res : hubRoutes) {
				// 허브 배송 담당자도 순번대로 배정
				UUID hubDeliveryManagerId =
					deliveryManagerService.assignNextManager(res.startHubId(), ManagerType.HUB_STAFF);

				DeliveryRouteRecord route = DeliveryRouteRecord.create(
					sequence++,
					res.startHubId(),
					res.endHubId(),
					null, // 허브 간 이동이므로 목적지 주소 없음
					res.estimatedDistance(),
					res.estimatedTime(),
					hubDeliveryManagerId
				);
				routes.add(route);
			}


			DeliveryRouteRecord lastMileRoute = DeliveryRouteRecord.create(
				sequence, // ++
				event.endHubId(), // 출발: 도착 허브
				null,             // 도착: 허브가 아니므로 ID는 null
				event.destinationAddress(), // 도착: 고객 최종 주소
				0L, 0L,           // 예상 거리/시간
				companyManagerId
			);
			routes.add(lastMileRoute);

			// 4. 경로 할당 및 영속화 (Outbox 이벤트 자동 발행)
			delivery.assignRoutes(routes);
			deliveryRepo.save(delivery);

		} catch (Exception e) {
			// SAGA 실패 보상: 예외를 던져 롤백하지 않고, 실패 내역을 즉시 Outbox에 밀어넣음.
			try {
				DeliveryFailedEvent failedEvent = new DeliveryFailedEvent(event.orderId(), "경로 조회 또는 생성 실패: " + e.getMessage());
				DeliveryOutboxEvent outboxEvent = new DeliveryOutboxEvent(
					"Delivery",
					event.orderId().toString(),
					"fail",
					objectMapper.writeValueAsString(failedEvent)
				);
				outboxEventRepo.save(outboxEvent);
			} catch (Exception parseException) {
				log.error("Outbox 실패 이벤트 직렬화 중 오류 발생: orderId={}", event.orderId(), parseException);
			}
		}

		// 성공이든 보상 이벤트를 남겼든 이 이벤트에 대한 처리는 끝났다.
		markProcessed(eventId);
	}

	private void markProcessed(UUID eventId) {
		if (eventId != null) {
			processedEventRepo.save(new ProcessedEventEntity(eventId));
		}
	}

	@Transactional(readOnly = true)
	public boolean existsActiveDeliveryByHubId(UUID hubId) {
		return routeRepo.existsActiveRouteByHubId(hubId);
	}

	// 요청사항 2
	@Transactional(readOnly = true)
	public DeliveryRouteInfoResponse getDeliveryInfoByOrderId(UUID orderId) {
		Delivery delivery = deliveryRepo.findByOrderId(orderId).orElseThrow(
			() -> new IllegalArgumentException("해당 주문 배송 정보 x")
		);
		List<DeliveryRouteInfoResponse.DeliveryRouteInfoDetails> routes =
			delivery.getRoutes().stream().map(
				x -> new
					DeliveryRouteInfoResponse.DeliveryRouteInfoDetails(
						x.getSequence(),
						x.getDeliveryManagerId(),
						x.getStatus().name()
				)
			).toList();
		return new DeliveryRouteInfoResponse(
			delivery.getId(),
			delivery.getStartHubId(),
			delivery.getEndHubId(),
			delivery.getDestinationAddress().address(),
			delivery.getCompanyDeliveryManagerId(),
			routes
		);
	}

}
