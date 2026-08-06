package com.auditlog.infrastructure.persistence.repository;

import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Read/write repository for new audit events. Existing events are immutable.
 */
public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID>,
        JpaSpecificationExecutor<AuditEventEntity> {

    @Query(value = "select next value for audit_event_sequence", nativeQuery = true)
    long reserveNextChainSequence();

    @Query("select event from AuditEventEntity event left join fetch event.payload order by event.chainSequence asc")
    Stream<AuditEventEntity> streamAllWithPayloadByOrderByChainSequenceAsc();

    List<AuditEventEntity> findByRecordedAtLessThanEqualOrderByChainSequenceAsc(Instant recordedAt);
    List<AuditEventEntity> findByActorIdOrderByChainSequenceAsc(String actorId);
    List<AuditEventEntity> findByResourceIdOrderByChainSequenceAsc(String resourceId);
}
