package com.msa7.v1.order.infra.outbox;

public enum OutboxStatus {
	// 아직 발행되지 않았거나 재시도를 기다리는 상태
	PENDING,
	// 브로커가 ack로 수신을 확정한 상태
	PUBLISHED,
	// 재시도 한도를 초과해 DLQ로 넘긴 상태. 사람이 확인해야 한다.
	FAILED
}
