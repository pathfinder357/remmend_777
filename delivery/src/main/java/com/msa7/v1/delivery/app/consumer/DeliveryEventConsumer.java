package com.msa7.v1.delivery.app.consumer;

import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa7.v1.delivery.app.DeliveryService;
import com.msa7.v1.delivery.global.config.RabbitMqConfig;
import com.msa7.v1.delivery.presentation.dto.payload.OrderCreatedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DeliveryEventConsumer {
	private final DeliveryService deliveryService;
	private final ObjectMapper objectMapper;

	/*
	 * 주문 도메인에서 던진 이벤트 수신.
	 *
	 * eventId는 order-service의 Outbox Relay가 헤더로 넣어준다.
	 * 헤더가 없는 과거 메시지도 처리할 수 있도록 required = false로 둔다.
	 */
	@RabbitListener(queues = RabbitMqConfig.ORDER_CREATED_QUEUE)
	public void onOrderCreated(String message,
		@Header(name = "eventId", required = false) String eventId) {
		try {
			OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

			// 배송 서비스의 SAGA 전용 메서드 호출
			deliveryService.createDeliveryFromOrder(event, eventId == null ? null : UUID.fromString(eventId));
		} catch (Exception e) {
			throw new RuntimeException("주문 생성 이벤트 처리 실패", e);
		}
	}
}
