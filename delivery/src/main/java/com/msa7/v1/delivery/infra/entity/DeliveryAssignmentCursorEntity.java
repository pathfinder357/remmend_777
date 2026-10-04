package com.msa7.v1.delivery.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * 허브별·담당자 유형별로 "직전에 배정한 순번"을 기억한다.
 *
 * 이 값이 없으면 순번 기반 배정을 할 수 없어 매번 첫 담당자만 뽑히거나 랜덤에 의존하게 된다.
 */
@Entity
@Table(name = "p_delivery_assignment_cursor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAssignmentCursorEntity {

	// "{hubId}:{managerType}" 형태의 복합 키
	@Id
	@Column(name = "cursor_key", length = 100)
	private String cursorKey;

	@Column(name = "last_assigned_seq")
	private Integer lastAssignedSeq;

	public DeliveryAssignmentCursorEntity(String cursorKey, Integer lastAssignedSeq) {
		this.cursorKey = cursorKey;
		this.lastAssignedSeq = lastAssignedSeq;
	}

	public void updateLastAssignedSeq(Integer lastAssignedSeq) {
		this.lastAssignedSeq = lastAssignedSeq;
	}
}
