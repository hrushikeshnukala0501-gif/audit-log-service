package com.auditlog.application.service;

import com.auditlog.application.command.AppendAuditEventCommand;
import com.auditlog.application.result.AppendedAuditEvent;
import com.auditlog.config.AuditHashProperties;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.entity.AuditEventPayloadEntity;
import com.auditlog.infrastructure.persistence.entity.ChainHeadEntity;
import com.auditlog.infrastructure.persistence.repository.AuditEventPayloadRepository;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import com.auditlog.infrastructure.persistence.repository.ChainHeadRepository;
import com.auditlog.support.exception.AuditLogException;
import com.auditlog.support.exception.ErrorCode;
import com.auditlog.support.utility.Sha256HashGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Transactional append use case. It is the only application path that advances the chain head.
 */
@Service
public class AuditEventAppendService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventAppendService.class);

    private final ChainHeadRepository chainHeadRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditEventPayloadRepository payloadRepository;
    private final AesGcmPayloadProtector payloadProtector;
    private final AuditEventHashInputFactory hashInputFactory;
    private final Sha256HashGenerator hashGenerator;
    private final AuditHashProperties hashProperties;
    private final Clock utcClock;

    public AuditEventAppendService(
            ChainHeadRepository chainHeadRepository,
            AuditEventRepository auditEventRepository,
            AuditEventPayloadRepository payloadRepository,
            AesGcmPayloadProtector payloadProtector,
            AuditEventHashInputFactory hashInputFactory,
            Sha256HashGenerator hashGenerator,
            AuditHashProperties hashProperties,
            Clock utcClock) {
        this.chainHeadRepository = chainHeadRepository;
        this.auditEventRepository = auditEventRepository;
        this.payloadRepository = payloadRepository;
        this.payloadProtector = payloadProtector;
        this.hashInputFactory = hashInputFactory;
        this.hashGenerator = hashGenerator;
        this.hashProperties = hashProperties;
        this.utcClock = utcClock;
    }

    @Transactional
    public AppendedAuditEvent append(AppendAuditEventCommand command) {
        ChainHeadEntity chainHead = chainHeadRepository.findByChainIdForUpdate(ChainHeadEntity.GLOBAL_CHAIN_ID)
                .orElseThrow(() -> new AuditLogException(ErrorCode.INTERNAL_ERROR, "Global chain head is missing"));

        long chainSequence = auditEventRepository.reserveNextChainSequence();
        if (chainSequence <= chainHead.getHeadSequence()) {
            throw new AuditLogException(ErrorCode.INTERNAL_ERROR, "Reserved chain sequence is not ahead of chain head");
        }

        // H2 (and common relational timestamp columns) persist microsecond precision.
        // Hash the exact precision that will be stored so later verification is stable.
        Instant recordedAt = Instant.now(utcClock).truncatedTo(ChronoUnit.MICROS);
        UUID eventId = UUID.randomUUID();
        ProtectedPayload protectedPayload = payloadProtector.protect(command.payload());
        String previousHash = chainHead.getHeadHash();
        String contentHash = hashGenerator.hash(hashInputFactory.create(
                eventId, chainSequence, command, recordedAt, protectedPayload, previousHash));

        AuditEventEntity auditEvent = new AuditEventEntity(
                eventId,
                chainSequence,
                command.eventType(),
                command.actorId(),
                command.resourceType(),
                command.resourceId(),
                recordedAt,
                protectedPayload.plaintextCommitment(),
                protectedPayload.ciphertextHash(),
                previousHash,
                contentHash,
                hashProperties.algorithm(),
                hashProperties.version());

        AuditEventEntity persistedAuditEvent = auditEventRepository.saveAndFlush(auditEvent);
        payloadRepository.save(new AuditEventPayloadEntity(
                persistedAuditEvent,
                protectedPayload.algorithm(),
                protectedPayload.keyReference(),
                protectedPayload.nonce(),
                protectedPayload.ciphertext(),
                recordedAt));
        chainHead.advanceTo(persistedAuditEvent, recordedAt);

        LOGGER.info("Appended audit event id={} sequence={} eventType={}", eventId, chainSequence, command.eventType());
        return new AppendedAuditEvent(
                eventId,
                chainSequence,
                recordedAt,
                previousHash,
                contentHash,
                hashProperties.algorithm(),
                hashProperties.version());
    }
}
