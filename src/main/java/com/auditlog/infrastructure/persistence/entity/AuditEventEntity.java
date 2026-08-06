package com.auditlog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable persistence representation of an append-only audit event.
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "chain_sequence", nullable = false, updatable = false)
    private Long chainSequence;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 255)
    private String actorId;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 255)
    private String resourceId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "payload_commitment", nullable = false, updatable = false, length = 64)
    private String payloadCommitment;

    @Column(name = "payload_ciphertext_hash", nullable = false, updatable = false, length = 64)
    private String payloadCiphertextHash;

    @Column(name = "previous_hash", nullable = false, updatable = false, length = 64)
    private String previousHash;

    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    @Column(name = "hash_algorithm", nullable = false, updatable = false, length = 16)
    private String hashAlgorithm;

    @Column(name = "hash_version", nullable = false, updatable = false)
    private short hashVersion;

    @OneToOne(mappedBy = "auditEvent", fetch = FetchType.LAZY)
    private AuditEventPayloadEntity payload;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(
            UUID eventId,
            long chainSequence,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            Instant recordedAt,
            String payloadCommitment,
            String payloadCiphertextHash,
            String previousHash,
            String contentHash,
            String hashAlgorithm,
            short hashVersion) {
        this.eventId = eventId;
        this.chainSequence = chainSequence;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.recordedAt = recordedAt;
        this.payloadCommitment = payloadCommitment;
        this.payloadCiphertextHash = payloadCiphertextHash;
        this.previousHash = previousHash;
        this.contentHash = contentHash;
        this.hashAlgorithm = hashAlgorithm;
        this.hashVersion = hashVersion;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getChainSequence() {
        return chainSequence;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getPayloadCommitment() {
        return payloadCommitment;
    }

    public String getPayloadCiphertextHash() {
        return payloadCiphertextHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public short getHashVersion() {
        return hashVersion;
    }

    public AuditEventPayloadEntity getPayload() {
        return payload;
    }
}
