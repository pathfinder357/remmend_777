package com.msa7.v1.order.app;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.msa7.v1.order.infra.feign.DeliveryClient;
import com.msa7.v1.order.domain.aggregate.Order;
import com.msa7.v1.order.domain.repo.OrderRepo;
import com.msa7.v1.order.domain.vo.OrderStatus;
import com.msa7.v1.order.infra.feign.InventoryClient;
import com.msa7.v1.order.presentation.dto.onlycontoller.RestApiResponse;
import com.msa7.v1.order.presentation.dto.payload.DeliveryResponse;
import com.msa7.v1.order.presentation.dto.payload.OrderWithDeliveryDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepo orderRepo;
	private final InventoryClient inventoryClient;
	private final DeliveryClient deliveryClient;

	@Transactional
	public Order createOrder(UUID receiverCompanyId, UUID productId, Integer quantity, String requests,
		UUID receiverSlackId, UUID startHubId, UUID endHubId, String destinationAddress
	) {
		// 재고를 먼저 차감한다. 재고가 부족하면 product-service가 409를 반환하므로 주문 자체가 생성되지 않는다.
		inventoryClient.decreaseInventory(productId, quantity);

		// 재고 차감은 이 트랜잭션에 포함되지 않는 원격 호출이므로, 커밋이 실패하면 직접 되돌려야 한다.
		registerInventoryRestoreOnRollback(productId, quantity);

		// 도메인 생성 (이벤트 등록)
		Order order = Order.create(receiverCompanyId, productId, quantity, requests
		, receiverSlackId, startHubId, endHubId, destinationAddress);
		return orderRepo.save(order);
	}

	@Transactional
	public Order getOrder(UUID orderId) {
		return orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
	}

	@Transactional
	public void compeleteOrderSaga(UUID orderId, UUID deliveryId) {
		Order order = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
		order.startDelivery(deliveryId);
		orderRepo.save(order);
	}

	@Transactional
	public void failOrderSaga(UUID orderId, String reason) {
		Order order = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));

		// 같은 실패 이벤트가 재전달되어도 재고를 두 번 복구하지 않는다.
		if (order.getStatus() == OrderStatus.CANCELLED) {
			log.info("이미 취소된 주문입니다. 재고 복구를 건너뜁니다. orderId={}", orderId);
			return;
		}

		log.warn("배송 생성 실패로 주문을 취소합니다. orderId={}, reason={}", orderId, reason);
		order.cancelOrder();
		orderRepo.save(order);

		// 주문 생성 시 차감했던 재고를 되돌린다.
		inventoryClient.restoreInventory(order.getProductId(), order.getQuantity().value());
	}

	// 주문ID만 주면 배송상태를 알수 있는 api(필규님 요청)
	@Transactional(readOnly = true)
	public OrderWithDeliveryDto getOrderWithDeliveryStatus(UUID orderId) {
		Order order = orderRepo.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found"));
		if (order.getDeliveryId() == null) {
			return new OrderWithDeliveryDto(order, "배송 정보 없음");
		}
		String deliveryStatus;
		try {
			RestApiResponse<DeliveryResponse> response = deliveryClient.
				getDeliveryInfo(order.getDeliveryId());
				deliveryStatus = Optional.ofNullable(response)
					.map(RestApiResponse::data)
					.map(DeliveryResponse::status)
					.orElse("배송 정보 없음");
		} catch (Exception e){
			log.error("배송 조회 실패 deliveryId: {}", order.getDeliveryId(), e);
			deliveryStatus = "배송 정보 조회 실패";
		}
		return new OrderWithDeliveryDto(order, deliveryStatus);
	}

	/*
	 * 주문 트랜잭션이 롤백되면 이미 차감한 재고를 복구한다.
	 *
	 * 복구 호출 자체가 실패하면 재고만 줄어든 상태로 남으므로, 로그를 남겨 수동 보정이 가능하게 한다.
	 */
	private void registerInventoryRestoreOnRollback(UUID productId, Integer quantity) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
					return;
				}
				try {
					inventoryClient.restoreInventory(productId, quantity);
					log.info("주문 롤백으로 재고를 복구했습니다. productId={}, quantity={}", productId, quantity);
				} catch (Exception e) {
					log.error("주문 롤백 후 재고 복구 실패. 수동 보정 필요 productId={}, quantity={}", productId, quantity, e);
				}
			}
		});
	}

}
