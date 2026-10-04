package com.msa7.v1.delivery.infra.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;

import com.msa7.v1.delivery.domain.vo.ManagerType;
import com.msa7.v1.delivery.infra.persist.BaseEntity;

@Entity
@Table(name = "p_delivery_manager")
@SQLDelete(sql = "UPDATE p_delivery_manager SET deleted_at = NOW() WHERE id = ?")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryManagerEntity extends BaseEntity {
	@Id
	@Column(name = "id")
	private UUID id; // userId

	@Column(name = "hub_id")
	private UUID hubId;

	@Column(name = "slack_id")
	private UUID slackId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type")
	private ManagerType type;

	@Column(name = "assignment_seq")
	private Integer assignmentSeq;


	public DeliveryManagerEntity(UUID id, UUID hubId, UUID slackId, ManagerType type,
		Integer assignmentSeq) { // String -> UUID 변경
		this.id = id;
		this.hubId = hubId;
		this.slackId = slackId;
		this.type = type;
		this.assignmentSeq = assignmentSeq;
	}

	// 영속 상태의 엔티티에 변경분을 반영한다. 트랜잭션 종료 시 변경 감지로 UPDATE된다.
	public void update(UUID hubId, UUID slackId, ManagerType type, Integer assignmentSeq) {
		this.hubId = hubId;
		this.slackId = slackId;
		this.type = type;
		this.assignmentSeq = assignmentSeq;
	}
}