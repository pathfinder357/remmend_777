package com.msa7.v1.delivery.infra.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.msa7.v1.delivery.infra.entity.DeliveryAssignmentCursorEntity;

import jakarta.persistence.LockModeType;

public interface JpaDeliveryAssignmentCursorRepository
	extends JpaRepository<DeliveryAssignmentCursorEntity, String> {

	/*
	 * 배정 순번을 읽는 동안 다른 트랜잭션이 같은 커서를 건드리지 못하게 잠근다.
	 * 잠그지 않으면 동시에 들어온 두 주문이 같은 담당자에게 배정된다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM DeliveryAssignmentCursorEntity c WHERE c.cursorKey = :cursorKey")
	Optional<DeliveryAssignmentCursorEntity> findByKeyForUpdate(@Param("cursorKey") String cursorKey);
}
