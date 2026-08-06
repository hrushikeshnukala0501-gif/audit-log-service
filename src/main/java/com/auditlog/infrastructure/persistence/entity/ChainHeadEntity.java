package com.auditlog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Mutable coordination row that serializes appends to the global audit chain.
 */
@Entity
@Table(name = "chain_head")
public class ChainHeadEntity {

    public static final short GLOBAL_CHAIN_ID = 1;

    @Id
    @Column(name = "chain_id", nullable = false, updatable = false)
    private short chainId;

    @Column(name = "head_sequence", nullable = false)
    private long headSequence;

    @Column(name = "head_hash", nullable = false, length = 64)
    private String headHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_event_id")
    private AuditEventEntity headEvent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChainHeadEntity() {
    }

    public short getChainId() {
        return chainId;
    }

    public long getHeadSequence() {
        return headSequence;
    }

    public String getHeadHash() {
        return headHash;
    }

    public AuditEventEntity getHeadEvent() {
        return headEvent;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void advanceTo(AuditEventEntity auditEvent, Instant updatedAt) {
        if (auditEvent.getChainSequence() == null) {
            throw new IllegalArgumentException("An audit event must have a database-assigned chain sequence");
        }
        this.headSequence = auditEvent.getChainSequence();
        this.headHash = auditEvent.getContentHash();
        this.headEvent = auditEvent;
        this.updatedAt = updatedAt;
    }
}
