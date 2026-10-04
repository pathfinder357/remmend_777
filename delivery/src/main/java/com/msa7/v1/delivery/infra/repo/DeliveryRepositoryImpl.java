package com.msa7.v1.delivery.infra.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import com.msa7.v1.delivery.domain.aggregateDelivery.Delivery;
import com.msa7.v1.delivery.domain.aggregateDelivery.DeliveryRouteRecord;
import com.msa7.v1.delivery.domain.repo.DeliveryRepo;
import com.msa7.v1.delivery.domain.vo.ActualRouteMetrics;
import com.msa7.v1.delivery.domain.vo.RouteMetrics;
import com.msa7.v1.delivery.infra.entity.DeliveryEntity;
import com.msa7.v1.delivery.infra.entity.DeliveryRouteRecordEntity;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DeliveryRepositoryImpl implements DeliveryRepo {

	private final JpaDeliveryRepository jpaDeliveryRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public Delivery save(Delivery delivery) {
		// Domain Model -> JPA Entity 변환
		DeliveryEntity entity = toEntity(delivery);
		DeliveryEntity savedEntity = jpaDeliveryRepository.save(entity);

		delivery.getDomainEvents().forEach(eventPublisher::publishEvent);
		delivery.clearEvents();

		// JPA Entity -> Domain Model 변환 후 반환
		return toDomain(savedEntity);
	}

	@Override
	public Optional<Delivery> findById(UUID id) {
		return jpaDeliveryRepository.findById(id)
			.map(this::toDomain);
	}

	/*
	 * 조회한 영속 엔티티에 직접 삭제 정보를 남긴다.
	 * 도메인 객체를 다시 엔티티로 변환해 저장하면 삭제 정보가 빠진 새 엔티티로 덮어써진다.
	 */
	@Override
	public void deleteById(UUID id, UUID deletedBy) {
		jpaDeliveryRepository.findById(id).ifPresent(entity -> entity.delete(deletedBy));
	}

	@Override
	public Optional<Delivery> findByOrderId(UUID orderId) {
		return jpaDeliveryRepository.findByOrderIdAndDeletedAtIsNull(orderId)
			.map(this::toDomain);
	}

	// Mapper 로직 (MapStruct 등을 활용할 수 있음)
	private DeliveryEntity toEntity(Delivery domain) {
		// 1. 부모 엔티티 생성
		DeliveryEntity entity = DeliveryEntity.builder()
			.id(domain.getId())
			.orderId(domain.getOrderId())
			.status(domain.getStatus())
			.startHubId(domain.getStartHubId())
			.endHubId(domain.getEndHubId())
			.destinationAddress(domain.getDestinationAddress() != null ? domain.getDestinationAddress().address() : null)
			.receiverName(domain.getReceiverName())
			.receiverSlackId(domain.getReceiverSlackId())
			.companyDeliveryManagerId(domain.getCompanyDeliveryManagerId())
			.build();

		// 2. 자식 엔티티 매핑 및 연관관계 설정
		if (domain.getRoutes() != null) {
			domain.getRoutes().forEach(route -> {
				DeliveryRouteRecordEntity routeEntity = DeliveryRouteRecordEntity.builder()
					.id(route.getId())
					.sequence(route.getSequence())
					.startHubId(route.getStartHubId())
					.endHubId(route.getEndHubId())
					.estimatedDistance(route.getMetrics() != null ? route.getMetrics().distance() : null)
					.estimatedTime(route.getMetrics() != null ? route.getMetrics().time() : null)
					.actualDistance(route.getActualMetrics() != null ? route.getActualMetrics().actualDistance() : null)
					.actualTime(route.getActualMetrics() != null ? route.getActualMetrics().actualTime() : null)
					.status(route.getStatus())
					.deliveryManagerId(route.getDeliveryManagerId())
					.destinationAddress(route.getDestinationAddress())
					.build();

				entity.addRoute(routeEntity);
			});
		}

		return entity;
	}

	private Delivery toDomain(DeliveryEntity entity) {
		// 1. 하위 엔티티 -> 도메인 변환
		List<DeliveryRouteRecord> routes = entity.getRoutes().stream()
			.map(routeEntity -> DeliveryRouteRecord.builder()
				.id(routeEntity.getId())
				.sequence(routeEntity.getSequence())
				.startHubId(routeEntity.getStartHubId())
				.endHubId(routeEntity.getEndHubId())
				//  VO(Value Object) 객체 생성 후 전달
				.metrics(new RouteMetrics(routeEntity.getEstimatedDistance(), routeEntity.getEstimatedTime()))
				.actualMetrics(new ActualRouteMetrics(routeEntity.getActualDistance(), routeEntity.getActualTime()))
				.status(routeEntity.getStatus())
				.deliveryManagerId(routeEntity.getDeliveryManagerId())
				.build())
			.collect(Collectors.toList());

		// 2. 루트 엔티티 -> 도메인 변환
		Delivery delivery = Delivery.builder()
			.id(entity.getId())
			.orderId(entity.getOrderId())
			.status(entity.getStatus())
			.startHubId(entity.getStartHubId())
			.endHubId(entity.getEndHubId())
			.destinationAddress(entity.getDestinationAddress())
			.receiverName(entity.getReceiverName())
			.receiverSlackId(entity.getReceiverSlackId())
			.companyDeliveryManagerId(entity.getCompanyDeliveryManagerId())
			.destinationAddress(entity.getDestinationAddress())
			.build();

		// 3. 변환된 routes 리스트 추가
		delivery.getRoutes().addAll(routes);

		return delivery;
	}

}
