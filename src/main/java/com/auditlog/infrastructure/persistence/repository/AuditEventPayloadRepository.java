package com.auditlog.infrastructure.persistence.repository;

import com.auditlog.infrastructure.persistence.entity.AuditEventPayloadEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuditEventPayloadRepository extends JpaRepository<AuditEventPayloadEntity, UUID> {

    List<AuditEventPayloadEntity> findByEventIdIn(Collection<UUID> eventIds);
}
