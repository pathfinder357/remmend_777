package com.msa7.v1.delivery.infra.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.msa7.v1.delivery.domain.vo.ManagerType;
import com.msa7.v1.delivery.infra.entity.DeliveryManagerEntity;

public interface JpaDeliveryManagerRepository extends JpaRepository<DeliveryManagerEntity, UUID> {
	@Query("SELECT MAX(m.assignmentSeq) FROM DeliveryManagerEntity m")
	Optional<Integer> findMaxSequence();

	// 논리 삭제된 담당자에게는 배송을 배정하지 않는다.
	Optional<DeliveryManagerEntity> findFirstByHubIdAndTypeAndAssignmentSeqGreaterThanAndDeletedAtIsNullOrderByAssignmentSeqAsc(
		UUID hubId, ManagerType type, Integer assignmentSeq
	);
}
