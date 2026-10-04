package com.msa7.v1.delivery.infra.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.msa7.v1.delivery.infra.entity.ProcessedEventEntity;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {
}
