package com.msa7.v1.order.infra.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.msa7.v1.order.global.config.RabbitConfig;
import com.msa7.v1.order.infra.entity.OrderOutboxEntity;
import com.msa7.v1.order.infra.repo.OrderOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// outbox->> RabbitMq
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelay {

	// 소비자가 중복 수신을 걸러낼 수 있도록 이벤트 식별자를 헤더로 함께 보낸다.
	public static final String EVENT_ID_HEADER = "eventId";

	private static final int BATCH_SIZE = 100;
	private static final int MAX_RETRY = 5;
	private static final long CONFIRM_TIMEOUT_SECONDS = 5L;
	private static final long BASE_BACKOFF_SECONDS = 5L;

	private final OrderOutboxRepository orderOutboxRepo;
	private final RabbitTemplate rabbitTemplate;

	/*
	 * 미발행 이벤트를 배치로 발행한다.
	 *
	 * - 한 번에 BATCH_SIZE개만 가져와 이벤트가 쌓여도 한 실행이 무한정 길어지지 않게 한다.
	 * - 조회 시 SKIP LOCKED로 잠그므로 인스턴스가 여러 대여도 같은 행을 중복 발행하지 않는다.
	 * - 브로커의 ack를 확인한 뒤에만 PUBLISHED로 표시한다.
	 */
	@Scheduled(fixedDelay = 5000)
	@Transactional
	public void publishOutboxMessages() {
		List<OrderOutboxEntity> messages = orderOutboxRepo.findPublishable(
			OutboxStatus.PENDING, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

		for (OrderOutboxEntity message : messages) {
			try {
				publishWithConfirm(message);
				message.markAsPublished();
			} catch (Exception e) {
				handleFailure(message, e);
			}
		}
	}

	private void publishWithConfirm(OrderOutboxEntity message) throws Exception {
		CorrelationData correlationData = new CorrelationData(message.getId().toString());

		rabbitTemplate.convertAndSend(
			RabbitConfig.ORDER_EXCHANGE,
			RabbitConfig.ORDER_CREATED_ROUTING_KEY,
			message.getPayload(),
			amqpMessage -> {
				amqpMessage.getMessageProperties().setHeader(EVENT_ID_HEADER, message.getId().toString());
				return amqpMessage;
			},
			correlationData);

		// ack를 기다리지 않으면 브로커가 못 받은 메시지도 발행 완료로 표시된다.
		CorrelationData.Confirm confirm =
			correlationData.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		if (confirm == null || !confirm.isAck()) {
			throw new IllegalStateException(
				"브로커가 메시지를 확인(ack)하지 않았습니다: " + (confirm == null ? "no confirm" : confirm.getReason()));
		}
	}

	private void handleFailure(OrderOutboxEntity message, Exception e) {
		int attempted = message.getRetryCount() + 1;

		if (attempted >= MAX_RETRY) {
			message.markAsFailed(e.getMessage());
			sendToDeadLetter(message);
			log.error("재시도 한도({})를 초과해 DLQ로 보냅니다. eventId={}", MAX_RETRY, message.getId(), e);
			return;
		}

		message.markAsRetryable(e.getMessage(), LocalDateTime.now().plusSeconds(backoffSeconds(attempted)));
		log.warn("이벤트 발행 실패, 재시도 예정. eventId={}, retryCount={}", message.getId(), attempted, e);
	}

	private void sendToDeadLetter(OrderOutboxEntity message) {
		try {
			rabbitTemplate.convertAndSend(RabbitConfig.ORDER_DLX, RabbitConfig.ORDER_DLQ_ROUTING_KEY,
				message.getPayload());
		} catch (Exception e) {
			// DLQ 전송까지 실패해도 상태는 FAILED로 남으므로 DB에서 추적할 수 있다.
			log.error("DLQ 전송마저 실패했습니다. eventId={}", message.getId(), e);
		}
	}

	// 5초, 10초, 20초, 40초 순으로 재시도 간격을 늘린다.
	private long backoffSeconds(int attempted) {
		return BASE_BACKOFF_SECONDS * (1L << (attempted - 1));
	}
}
