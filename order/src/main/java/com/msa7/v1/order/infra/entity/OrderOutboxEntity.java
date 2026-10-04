package com.msa7.v1.order.infra.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.msa7.v1.order.infra.outbox.OutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "p_order_outbox",
	// Relay가 매 5초마다 조회하는 조건이므로 인덱스를 둔다.
	indexes = @Index(name = "idx_order_outbox_status_next_retry", columnList = "status, next_retry_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEntity {

	private static final int MAX_ERROR_LENGTH = 500;

	@Id
	private UUID id;
	private String aggregateType;
	private String aggregateId;
	private String eventType;
	@Column(columnDefinition = "TEXT")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20)
	private OutboxStatus status;

	// 발행을 시도한 횟수. 재시도 한도 판단에 사용한다.
	@Column(name = "retry_count")
	private int retryCount;

	// 이 시각 이후에만 다시 발행을 시도한다. 실패할수록 뒤로 미뤄 브로커 부하를 줄인다.
	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	// 마지막 실패 원인. 운영 중 왜 막혔는지 DB만 보고 알 수 있게 남긴다.
	@Column(name = "last_error", length = MAX_ERROR_LENGTH)
	private String lastError;

	private LocalDateTime publishedAt;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public OrderOutboxEntity(String aggregateType, String aggregateId, String eventType, String payload) {
		this.id = UUID.randomUUID();
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.retryCount = 0;
		this.nextRetryAt = LocalDateTime.now();
		this.createdAt = LocalDateTime.now();
	}

	// 브로커가 ack를 준 뒤에만 호출한다.
	public void markAsPublished() {
		this.status = OutboxStatus.PUBLISHED;
		this.publishedAt = LocalDateTime.now();
	}

	// 실패했지만 아직 재시도 여지가 있는 경우
	public void markAsRetryable(String error, LocalDateTime nextRetryAt) {
		this.retryCount++;
		this.lastError = truncate(error);
		this.nextRetryAt = nextRetryAt;
	}

	// 재시도 한도를 초과한 경우. 더 이상 Relay가 집어가지 않는다.
	public void markAsFailed(String error) {
		this.status = OutboxStatus.FAILED;
		this.retryCount++;
		this.lastError = truncate(error);
	}

	private static String truncate(String error) {
		if (error == null) {
			return null;
		}
		return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
	}
}
