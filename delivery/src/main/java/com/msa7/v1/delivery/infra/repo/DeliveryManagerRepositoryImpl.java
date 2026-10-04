package com.msa7.v1.delivery.infra.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.msa7.v1.delivery.domain.aggregateManager.DeliveryManager;
import com.msa7.v1.delivery.domain.repo.DeliveryManagerRepo;
import com.msa7.v1.delivery.domain.vo.ManagerType;
import com.msa7.v1.delivery.infra.entity.DeliveryManagerEntity;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DeliveryManagerRepositoryImpl implements DeliveryManagerRepo {
	private final JpaDeliveryManagerRepository jpaRepo;

	/*
	 * 기존 담당자가 있으면 감사·삭제 정보를 유지한 채 변경분만 반영한다.
	 * (기존 엔티티를 그대로 저장하기만 하면 수정 API가 성공해도 DB 값이 바뀌지 않는다.)
	 */
	@Override
	public DeliveryManager save(DeliveryManager manager) {
		DeliveryManagerEntity entity = jpaRepo.findById(manager.getId())
			.map(existing -> {
				existing.update(manager.getHubId(), manager.getSlackId(), manager.getType(),
					manager.getAssignmentSeq());
				return existing;
			})
			.orElseGet(() -> toEntity(manager));

		return toDomain(jpaRepo.save(entity));
	}

	@Override
	public Optional<DeliveryManager> findById(UUID id) {
		return jpaRepo.findById(id).map(this::toDomain);
	}

	@Override
	public Optional<Integer> findMaxSequence() {
		return jpaRepo.findMaxSequence();
	}

	@Override
	public Optional<DeliveryManager> findNextAvailableManager(UUID hubId, ManagerType type, Integer lastAssignedSeq) {
		return jpaRepo
			.findFirstByHubIdAndTypeAndAssignmentSeqGreaterThanAndDeletedAtIsNullOrderByAssignmentSeqAsc(
				hubId, type, lastAssignedSeq)
			.map(this::toDomain);
	}

	private DeliveryManagerEntity toEntity(DeliveryManager domain) {
		return new DeliveryManagerEntity(
			domain.getId(),
			domain.getHubId(),
			domain.getSlackId(),
			domain.getType(),
			domain.getAssignmentSeq()
		);
	}
	private DeliveryManager toDomain(DeliveryManagerEntity entity) {
		return DeliveryManager.builder()
			.id(entity.getId())
			.hubId(entity.getHubId())
			.slackId(entity.getSlackId())
			.type(entity.getType())
			.assignmentSeq(entity.getAssignmentSeq())
			.build();
	}

}
