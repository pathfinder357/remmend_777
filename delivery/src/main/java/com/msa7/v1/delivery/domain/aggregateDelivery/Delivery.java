package com.msa7.v1.delivery.domain.aggregateDelivery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.msa7.v1.delivery.domain.vo.DeliveryStatus;
import com.msa7.v1.delivery.domain.vo.DestinationAddress;
import com.msa7.v1.delivery.domain.vo.RouteStatus;
import com.msa7.v1.delivery.global.common.BusinessException;
import com.msa7.v1.delivery.global.common.ErrorCode;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryCompletedEvent;
import com.msa7.v1.delivery.presentation.dto.payload.DeliveryCreatedEvent;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Delivery {
	private final UUID id;
	private final UUID orderId;
	private final UUID startHubId;
	private final UUID endHubId;
	private final String receiverName;
	private final UUID receiverSlackId;
	private final UUID companyDeliveryManagerId;

	private DeliveryStatus status;
	private final DestinationAddress destinationAddress;
	private final List<DeliveryRouteRecord> routes= new ArrayList<>();

	// saga Event 버퍼
	private final List<Object> domainEvents = new ArrayList<>();

	@Builder
	public Delivery(UUID id, UUID orderId, DeliveryStatus status, UUID startHubId, UUID endHubId,
		String destinationAddress, String receiverName, UUID receiverSlackId, UUID companyDeliveryManagerId
	) {
		this.id = id;
		this.orderId = orderId;
		this.status = status;
		this.startHubId = startHubId;
		this.endHubId = endHubId;
		this.destinationAddress = new DestinationAddress(destinationAddress);
		this.receiverName = receiverName;
		this.receiverSlackId = receiverSlackId;
		this.companyDeliveryManagerId = companyDeliveryManagerId;

	}

	// public static Delivery create(UUID orderId, UUID startHubId, UUID endHubId,
	// 	String destinationAddress, String receiverName, UUID receiverSlackId, UUID companyDeliveryManagerId) {
	// 	return Delivery.builder()
	// 		.id(UUID.randomUUID())
	// 		.orderId(orderId)
	// 		.status(DeliveryStatus.HUB_WAITING)
	// 		.startHubId(startHubId)
	// 		.endHubId(endHubId)
	// 		.destinationAddress(destinationAddress)
	// 		.receiverName(receiverName)
	// 		.receiverSlackId(receiverSlackId)
	// 		.companyDeliveryManagerId(companyDeliveryManagerId)
	// 		.build();
	// }

	public static Delivery createFromOrder(UUID orderId,
		UUID startHubId, UUID endHubId,
		String destinationAddress,  UUID receiverSlackId,
		UUID companyDeliveryManagerId) {
		Delivery delivery = Delivery.builder()
			.id(UUID.randomUUID())
			.orderId(orderId)
			.status(DeliveryStatus.HUB_WAITING)
			.startHubId(startHubId)
			.endHubId(endHubId)
			.destinationAddress(destinationAddress)
			.receiverSlackId(receiverSlackId)
			.companyDeliveryManagerId(companyDeliveryManagerId)
			.build();
		delivery.domainEvents.add(new DeliveryCreatedEvent(orderId, delivery.getId()));
		return delivery;
	}



	// 전체 경로 최초 일괄 세팅
	public void assignRoutes(List<DeliveryRouteRecord> newRoutes) {
		if (!this.routes.isEmpty()) {
			throw new IllegalStateException("배송 경로는 이미 설정되어 있습니다.");
		}
		this.routes.addAll(newRoutes);
	}

	// 배송 상태 변경
	public void updateStatus(DeliveryStatus newStatus) {
		if (this.status == DeliveryStatus.COMPLETED) {
			throw new IllegalStateException("이미 완료된 배송은 상태를 변경할 수 없습니다.");
		}
		this.status = newStatus;
	}

	/*
	 * requesterId가 주어지면 본인에게 배정된 경로만 변경할 수 있다.
	 * 관리자(MASTER, HUB_MANAGER)처럼 담당자 확인이 필요 없는 요청은 null로 호출한다.
	 */
	public void updateRouteStatus(UUID routeId, RouteStatus newStatus, UUID requesterId) {
		DeliveryRouteRecord targetRoute = this.routes.stream()
			.filter(r -> r.getId().equals(routeId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("해당 경로를 찾을 수 없습니다."));

		if (requesterId != null && !requesterId.equals(targetRoute.getDeliveryManagerId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인에게 배정된 배송 경로만 변경할 수 있습니다.");
		}

		targetRoute.updateStatus(newStatus);
		boolean isAllCompleted = this.routes.stream()
			.allMatch(r -> r.getStatus() == RouteStatus.ARRIVED);
		if (isAllCompleted) {
			this.status = DeliveryStatus.COMPLETED;
			// Spring Data @DomainEvents를 통해 Outbox로 자동 발행됨
			registerEvent(new DeliveryCompletedEvent(this.orderId, this.id, "배송이 최종 완료되었습니다."));
		}
	}


	// 이벤트 방출용 Getter 및 Clear 메서드
	public void registerEvent(Object event) {this.domainEvents.add(event);}
	public List<Object> getDomainEvents() { return Collections.unmodifiableList(domainEvents); }
	public void clearEvents() { this.domainEvents.clear(); }
}



