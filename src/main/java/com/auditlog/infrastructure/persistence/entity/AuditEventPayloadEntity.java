package com.auditlog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Encrypted payload material for an immutable audit event.
 */
@Entity
@Immutable
@Table(name = "audit_event_payload")
public class AuditEventPayloadEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private AuditEventEntity auditEvent;

    @Column(name = "encryption_algorithm", nullable = false, updatable = false, length = 64)
    private String encryptionAlgorithm;

    @Column(name = "encryption_key_reference", nullable = false, updatable = false, length = 512)
    private String encryptionKeyReference;

    @Lob
    @Column(name = "encryption_nonce", nullable = false, updatable = false)
    private byte[] encryptionNonce;

    @Lob
    @Column(name = "ciphertext", nullable = false, updatable = false)
    private byte[] ciphertext;

    @Column(name = "stored_at", nullable = false, updatable = false)
    private Instant storedAt;

    protected AuditEventPayloadEntity() {
    }

    public AuditEventPayloadEntity(
            AuditEventEntity auditEvent,
            String encryptionAlgorithm,
            String encryptionKeyReference,
            byte[] encryptionNonce,
            byte[] ciphertext,
            Instant storedAt) {
        this.auditEvent = auditEvent;
        this.encryptionAlgorithm = encryptionAlgorithm;
        this.encryptionKeyReference = encryptionKeyReference;
        this.encryptionNonce = encryptionNonce.clone();
        this.ciphertext = ciphertext.clone();
        this.storedAt = storedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public String getEncryptionKeyReference() {
        return encryptionKeyReference;
    }

    public byte[] getEncryptionNonce() {
        return encryptionNonce.clone();
    }

    public byte[] getCiphertext() {
        return ciphertext.clone();
    }

    public Instant getStoredAt() {
        return storedAt;
    }
}
