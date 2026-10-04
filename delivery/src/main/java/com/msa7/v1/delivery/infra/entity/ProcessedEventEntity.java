package com.msa7.v1.delivery.infra.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 이미 처리한 메시지를 기록한다.
 *
 * RabbitMQ는 at-least-once이므로 같은 이벤트가 두 번 이상 전달될 수 있다.
 * 이 테이블이 있으면 재전달된 이벤트를 배송 생성까지 가기 전에 걸러낼 수 있다.
 */
@Entity
@Table(name = "p_delivery_processed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEventEntity {

	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	public ProcessedEventEntity(UUID eventId) {
		this.eventId = eventId;
		this.processedAt = LocalDateTime.now();
	}
}
