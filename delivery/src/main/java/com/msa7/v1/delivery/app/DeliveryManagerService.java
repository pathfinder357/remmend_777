package com.msa7.v1.delivery.app;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import com.msa7.v1.delivery.domain.aggregateManager.DeliveryManager;
import com.msa7.v1.delivery.domain.repo.DeliveryManagerRepo;
import com.msa7.v1.delivery.domain.vo.ManagerType;
import com.msa7.v1.delivery.infra.entity.DeliveryAssignmentCursorEntity;
import com.msa7.v1.delivery.infra.feign.HubClient;
import com.msa7.v1.delivery.infra.repo.JpaDeliveryAssignmentCursorRepository;

@Service
@RequiredArgsConstructor
public class DeliveryManagerService {

	// 아직 아무도 배정되지 않은 상태. 첫 담당자의 순번은 0이므로 그보다 작은 값이어야 한다.
	private static final int INITIAL_CURSOR = -1;

	private final DeliveryManagerRepo managerRepo;
	private final JpaDeliveryAssignmentCursorRepository cursorRepo;
	private final HubClient hubClient;

	@Transactional
	public UUID createDeliveryManager(UUID userId, UUID slackId, UUID hubId, ManagerType type) {
		if (hubId != null) {
			boolean exists = hubClient.checkHubExists(hubId);
			if (!exists) {
				throw new IllegalArgumentException("존재하지 않는 허브 ID 입니다.");
			}
		}

		// DeliveryManager.create()가 마지막 순번에 +1을 하므로 여기서는 마지막 순번을 그대로 넘긴다.
		int lastAssignmentSeq = managerRepo.findMaxSequence().orElse(INITIAL_CURSOR);

		DeliveryManager manager = DeliveryManager.create(userId, hubId, slackId, type, lastAssignmentSeq);
		managerRepo.save(manager);

		return manager.getId();
	}

	@Transactional
	public void updateDeliveryManager(UUID id, UUID newHubId, ManagerType type) {
		DeliveryManager manager = managerRepo.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("배송 담당자를 찾을 수 없습니다."));

		if (newHubId != null && !newHubId.equals(manager.getHubId())) {
			boolean exists = hubClient.checkHubExists(newHubId);
			if (!exists) {
				throw new IllegalArgumentException("존재하지 않는 허브 ID 입니다.");
			}
		}

		manager.updateInfo(newHubId, type);
		managerRepo.save(manager);
	}

	/*
	 * 해당 허브의 다음 순번 담당자를 배정한다(README의 "순번 기반 배정").
	 *
	 * 커서보다 순번이 큰 첫 담당자를 뽑고, 마지막까지 갔으면 처음으로 돌아온다.
	 * 호출자의 트랜잭션에 참여하므로 커서 잠금은 배송 생성이 커밋될 때까지 유지된다.
	 */
	@Transactional
	public UUID assignNextManager(UUID hubId, ManagerType type) {
		String cursorKey = hubId + ":" + type.name();

		DeliveryAssignmentCursorEntity cursor = cursorRepo.findByKeyForUpdate(cursorKey)
			.orElseGet(() -> cursorRepo.save(new DeliveryAssignmentCursorEntity(cursorKey, INITIAL_CURSOR)));

		DeliveryManager next = managerRepo.findNextAvailableManager(hubId, type, cursor.getLastAssignedSeq())
			.or(() -> managerRepo.findNextAvailableManager(hubId, type, INITIAL_CURSOR))
			.orElseThrow(() -> new IllegalStateException(
				"배정 가능한 배송 담당자가 없습니다. hubId=" + hubId + ", type=" + type));

		cursor.updateLastAssignedSeq(next.getAssignmentSeq());

		return next.getId();
	}
}
