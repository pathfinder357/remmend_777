package com.msa7.v1.order.domain.aggregate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.msa7.v1.order.domain.vo.OrderStatus;
import com.msa7.v1.order.domain.vo.Quantity;
import com.msa7.v1.order.domain.vo.RequestNotes;
import com.msa7.v1.order.presentation.dto.payload.OrderCreatedEvent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter

public class Order {
	private final UUID id;

	// 외부 도메인 참조는 오직 ID로만
	private final UUID receiverCompanyId;
	private final UUID productId;
	private UUID deliveryId;

	// Value Obj
	private  Quantity quantity;
	private  OrderStatus status;
	private RequestNotes requestNotes;

	// 주문 내 이벤트 버퍼(outBox 패턴 트리거 + saga)
	private final List<Object> domainEvents = new ArrayList<>();

	@Builder
	public Order(UUID id, UUID receiverCompanyId, UUID productId,
		UUID deliveryId,Integer quantity, OrderStatus status, String requestNotes) {
		this.id = id;
		this.receiverCompanyId = receiverCompanyId;
		this.productId = productId;
		this.deliveryId = deliveryId;
		this.quantity = new Quantity(quantity);
		this.status = status;
		this.requestNotes = new RequestNotes(requestNotes);
	}

	public static Order create(UUID receiverCompanyId, UUID productId,
		Integer quantity, String requestNotes, UUID receiverSlackId, UUID startHubId,
		UUID endHubId, String destinationAddress) {
		Order order = Order.builder()
			.id(UUID.randomUUID())
			.receiverCompanyId(receiverCompanyId)
			.productId(productId)
			.quantity(quantity)
			.status(OrderStatus.PENDING)// c초기 상태
			.requestNotes(requestNotes)
			.build();
		// 주문 생성 이벤트 등록(outbox -> MQ)
		order.registerEvent(new OrderCreatedEvent(
			order.getId(), receiverCompanyId, receiverSlackId, startHubId, endHubId, destinationAddress
		));
		return order;
	}

	// callBack메서드 배송 생성 성공시 상태 변경 및 배송 ID 할당+ SAGA 완료시 상태
	public void startDelivery(UUID deliveryId) {
		this.deliveryId = deliveryId;
		this.status = OrderStatus.SHIPPED;
	}

	// 배송 생성 실패시 주문 취소
	public void cancelOrder() {
		this.status = OrderStatus.CANCELLED;
	}

	public void update(Integer newQuantity, String newNotes) {
		if (this.status != OrderStatus.PENDING) {
			throw new IllegalStateException("배송 전 상태(PENDING)에서만 수정 가능.");
		}
		this.quantity = new Quantity(newQuantity);
		this.requestNotes = new RequestNotes(newNotes);
	}

	public void registerEvent(Object event) {
		this.domainEvents.add(event);
	}

	public List<Object> getDomainEvents(){
		return Collections.unmodifiableList(domainEvents);
	}

	public void clearEvents() { this.domainEvents.clear();}
}
