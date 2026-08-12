package com.auditlog.application.service;

import com.auditlog.application.result.ChainVerificationResult;
import com.auditlog.application.port.HashGenerator;
import com.auditlog.application.port.PayloadProtectionException;
import com.auditlog.application.port.PayloadProtector;
import com.auditlog.config.AuditHashProperties;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.entity.AuditEventPayloadEntity;
import com.auditlog.infrastructure.persistence.entity.ChainHeadEntity;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import com.auditlog.infrastructure.persistence.repository.ChainHeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
    private static final int PERSISTENCE_CONTEXT_CLEAR_INTERVAL = 100;

    private final AuditEventRepository auditEventRepository;
    private final ChainHeadRepository chainHeadRepository;
    private final PayloadProtector payloadProtector;
    private final AuditEventHashInputFactory hashInputFactory;
    private final HashGenerator hashGenerator;
    private final AuditHashProperties hashProperties;
    private final Clock utcClock;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditChainVerificationService(
            AuditEventRepository auditEventRepository,
            ChainHeadRepository chainHeadRepository,
            PayloadProtector payloadProtector,
            AuditEventHashInputFactory hashInputFactory,
            HashGenerator hashGenerator,
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
        return verify(null, null);
    }

    @Transactional(readOnly = true)
    public ChainVerificationResult verify(Long fromSequence, Long toSequence) {
        validateRange(fromSequence, toSequence);
        Instant verifiedAt = Instant.now(utcClock);
        boolean completeChainVerification = fromSequence == null && toSequence == null;
        long startSequence = fromSequence == null ? 1 : fromSequence;
        VerificationStart start = startFor(startSequence);
        String expectedPreviousHash = start.expectedPreviousHash();
        long previousSequence = start.previousSequence();
        Long verifiedFromSequence = null;
        int verifiedEventCount = 0;

        try (Stream<AuditEventEntity> eventStream = streamEvents(completeChainVerification, startSequence, toSequence)) {
            Iterator<AuditEventEntity> events = eventStream.iterator();
            while (events.hasNext()) {
                AuditEventEntity event = events.next();
                if (verifiedFromSequence == null) {
                    verifiedFromSequence = event.getChainSequence();
                }
                ChainVerificationResult.Violation violation = verifyEvent(
                        event, event.getPayload(), expectedPreviousHash, previousSequence);
                if (violation != null) {
                    LOGGER.warn("Audit chain verification failed type={} eventId={} sequence={}",
                            violation.type(), violation.eventId(), violation.chainSequence());
                    return invalid(completeChainVerification, verifiedFromSequence, previousSequence, violation, verifiedAt);
                }
                expectedPreviousHash = event.getContentHash();
                previousSequence = event.getChainSequence();
                verifiedEventCount++;
                if (verifiedEventCount % PERSISTENCE_CONTEXT_CLEAR_INTERVAL == 0) {
                    entityManager.clear();
                }
            }
        }

        if (completeChainVerification) {
            ChainVerificationResult.Violation headViolation = verifyChainHead(previousSequence, expectedPreviousHash);
            if (headViolation != null) {
                LOGGER.warn("Audit chain verification failed type={}", headViolation.type());
                return invalid(true, verifiedFromSequence, previousSequence, headViolation, verifiedAt);
            }
        }

        LOGGER.info("Audit chain verification succeeded completeChain={} fromSequence={} throughSequence={}",
                completeChainVerification, verifiedFromSequence, previousSequence);
        return new ChainVerificationResult(
                true,
                completeChainVerification,
                verifiedFromSequence,
                previousSequence,
                null,
                verifiedAt);
    }

    private void validateRange(Long fromSequence, Long toSequence) {
        if (fromSequence != null && fromSequence < 1
                || toSequence != null && toSequence < 1
                || fromSequence != null && toSequence != null && fromSequence > toSequence) {
            throw new com.auditlog.support.exception.AuditLogException(
                    com.auditlog.support.exception.ErrorCode.MALFORMED_REQUEST,
                    "Verification sequence range is invalid");
        }
    }

    private VerificationStart startFor(long startSequence) {
        if (startSequence == 1) {
            return new VerificationStart(hashProperties.genesisHash(), 0);
        }
        return auditEventRepository.findTopByChainSequenceLessThanOrderByChainSequenceDesc(startSequence)
                .map(event -> new VerificationStart(event.getContentHash(), event.getChainSequence()))
                .orElse(new VerificationStart(hashProperties.genesisHash(), 0));
    }

    private Stream<AuditEventEntity> streamEvents(boolean completeChainVerification, long fromSequence, Long toSequence) {
        return completeChainVerification
                ? auditEventRepository.streamAllWithPayloadByOrderByChainSequenceAsc()
                : auditEventRepository.streamRangeWithPayloadByChainSequence(fromSequence, toSequence);
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
            boolean completeChainVerification,
            Long verifiedFromSequence,
            long verifiedThroughSequence,
            ChainVerificationResult.Violation violation,
            Instant verifiedAt) {
        return new ChainVerificationResult(
                false,
                completeChainVerification,
                verifiedFromSequence,
                verifiedThroughSequence,
                violation,
                verifiedAt);
    }

    private ChainVerificationResult.Violation violation(AuditEventEntity event, String type, String message) {
        return new ChainVerificationResult.Violation(event.getEventId(), event.getChainSequence(), type, message);
    }

    private record VerificationStart(String expectedPreviousHash, long previousSequence) {
    }
}
