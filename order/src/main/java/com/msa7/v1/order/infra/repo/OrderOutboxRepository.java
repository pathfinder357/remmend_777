package com.msa7.v1.order.infra.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.msa7.v1.order.infra.entity.OrderOutboxEntity;
import com.msa7.v1.order.infra.outbox.OutboxStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEntity, UUID> {

	/*
	 * 발행 대상 이벤트를 잠금과 함께 가져온다.
	 *
	 * PESSIMISTIC_WRITE + lock.timeout = -2 는 PostgreSQL의 SELECT ... FOR UPDATE SKIP LOCKED로 번역된다.
	 * 인스턴스가 여러 대여도 각자 다른 행만 집어가므로 같은 이벤트를 중복 발행하지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("SELECT o FROM OrderOutboxEntity o "
		+ "WHERE o.status = :status AND o.nextRetryAt <= :now "
		+ "ORDER BY o.createdAt ASC")
	List<OrderOutboxEntity> findPublishable(@Param("status") OutboxStatus status,
		@Param("now") LocalDateTime now, Pageable pageable);
}
