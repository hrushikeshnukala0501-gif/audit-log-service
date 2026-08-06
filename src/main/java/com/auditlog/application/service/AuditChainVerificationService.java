package com.auditlog.application.service;

import com.auditlog.application.result.ChainVerificationResult;
import com.auditlog.config.AuditHashProperties;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.entity.AuditEventPayloadEntity;
import com.auditlog.infrastructure.persistence.entity.ChainHeadEntity;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import com.auditlog.infrastructure.persistence.repository.ChainHeadRepository;
import com.auditlog.support.utility.Sha256HashGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Recomputes the complete chain and returns the first observed inconsistency.
 */
@Service
public class AuditChainVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditChainVerificationService.class);

    private final AuditEventRepository auditEventRepository;
    private final ChainHeadRepository chainHeadRepository;
    private final AesGcmPayloadProtector payloadProtector;
    private final AuditEventHashInputFactory hashInputFactory;
    private final Sha256HashGenerator hashGenerator;
    private final AuditHashProperties hashProperties;
    private final Clock utcClock;

    public AuditChainVerificationService(
            AuditEventRepository auditEventRepository,
            ChainHeadRepository chainHeadRepository,
            AesGcmPayloadProtector payloadProtector,
            AuditEventHashInputFactory hashInputFactory,
            Sha256HashGenerator hashGenerator,
            AuditHashProperties hashProperties,
            Clock utcClock) {
        this.auditEventRepository = auditEventRepository;
        this.chainHeadRepository = chainHeadRepository;
        this.payloadProtector = payloadProtector;
        this.hashInputFactory = hashInputFactory;
        this.hashGenerator = hashGenerator;
        this.hashProperties = hashProperties;
        this.utcClock = utcClock;
    }

    @Transactional(readOnly = true)
    public ChainVerificationResult verify() {
        Instant verifiedAt = Instant.now(utcClock);
        String expectedPreviousHash = hashProperties.genesisHash();
        long previousSequence = 0;

        try (Stream<AuditEventEntity> eventStream = auditEventRepository.streamAllWithPayloadByOrderByChainSequenceAsc()) {
            Iterator<AuditEventEntity> events = eventStream.iterator();
            while (events.hasNext()) {
                AuditEventEntity event = events.next();
                ChainVerificationResult.Violation violation = verifyEvent(
                        event, event.getPayload(), expectedPreviousHash, previousSequence);
                if (violation != null) {
                    LOGGER.warn("Audit chain verification failed type={} eventId={} sequence={}",
                            violation.type(), violation.eventId(), violation.chainSequence());
                    return invalid(previousSequence, violation, verifiedAt);
                }
                expectedPreviousHash = event.getContentHash();
                previousSequence = event.getChainSequence();
            }
        }

        ChainVerificationResult.Violation headViolation = verifyChainHead(previousSequence, expectedPreviousHash);
        if (headViolation != null) {
            LOGGER.warn("Audit chain verification failed type={}", headViolation.type());
            return invalid(previousSequence, headViolation, verifiedAt);
        }

        LOGGER.info("Audit chain verification succeeded through sequence={}", previousSequence);
        return new ChainVerificationResult(true, previousSequence, null, verifiedAt);
    }

    private ChainVerificationResult.Violation verifyEvent(
            AuditEventEntity event,
            AuditEventPayloadEntity payload,
            String expectedPreviousHash,
            long previousSequence) {
        if (event.getChainSequence() <= previousSequence) {
            return violation(event, "ORDER_VIOLATION", "Audit event sequence is not strictly increasing");
        }
        if (!event.getPreviousHash().equals(expectedPreviousHash)) {
            return violation(event, "PREDECESSOR_HASH_MISMATCH", "Previous hash does not match the expected chain link");
        }
        if (payload == null) {
            return violation(event, "PAYLOAD_MISSING", "Encrypted payload material is missing");
        }
        if (!hashGenerator.hash(payload.getCiphertext()).equals(event.getPayloadCiphertextHash())) {
            return violation(event, "PAYLOAD_CIPHERTEXT_MISMATCH", "Stored payload ciphertext has changed");
        }
        try {
            if (!hashGenerator.hash(payloadProtector.unprotect(
                    payload.getEncryptionAlgorithm(), payload.getEncryptionNonce(), payload.getCiphertext()))
                    .equals(event.getPayloadCommitment())) {
                return violation(event, "PAYLOAD_COMMITMENT_MISMATCH", "Decrypted payload does not match its commitment");
            }
        } catch (PayloadProtectionException exception) {
            return violation(event, "PAYLOAD_DECRYPTION_FAILURE", "Stored payload cannot be decrypted or authenticated");
        }
        String calculatedHash = hashGenerator.hash(hashInputFactory.create(
                event.getEventId(),
                event.getChainSequence(),
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getRecordedAt(),
                event.getPayloadCommitment(),
                event.getPayloadCiphertextHash(),
                event.getPreviousHash()));
        if (!calculatedHash.equals(event.getContentHash())) {
            return violation(event, "CONTENT_HASH_MISMATCH", "Stored event content does not match its content hash");
        }
        return null;
    }

    private ChainVerificationResult.Violation verifyChainHead(long lastSequence, String lastHash) {
        ChainHeadEntity chainHead = chainHeadRepository.findById(ChainHeadEntity.GLOBAL_CHAIN_ID).orElse(null);
        if (chainHead == null) {
            return new ChainVerificationResult.Violation(null, null, "CHAIN_HEAD_MISSING", "Global chain head is missing");
        }
        if (chainHead.getHeadSequence() != lastSequence || !chainHead.getHeadHash().equals(lastHash)) {
            return new ChainVerificationResult.Violation(
                    chainHead.getHeadEvent() == null ? null : chainHead.getHeadEvent().getEventId(),
                    chainHead.getHeadSequence(),
                    "CHAIN_HEAD_MISMATCH",
                    "Persisted chain head does not match the verified chain tail");
        }
        return null;
    }

    private ChainVerificationResult invalid(
            long verifiedThroughSequence,
            ChainVerificationResult.Violation violation,
            Instant verifiedAt) {
        return new ChainVerificationResult(false, verifiedThroughSequence, violation, verifiedAt);
    }

    private ChainVerificationResult.Violation violation(AuditEventEntity event, String type, String message) {
        return new ChainVerificationResult.Violation(event.getEventId(), event.getChainSequence(), type, message);
    }
}
