package com.auditlog.application.service;

import com.auditlog.application.command.AppendAuditEventCommand;
import com.auditlog.config.AuditHashProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the versioned, protected input for an audit-event content hash.
 */
@Component
public class AuditEventHashInputFactory {

    private final ObjectMapper objectMapper;
    private final AuditHashProperties hashProperties;

    public AuditEventHashInputFactory(ObjectMapper objectMapper, AuditHashProperties hashProperties) {
        this.objectMapper = objectMapper;
        this.hashProperties = hashProperties;
    }

    public ObjectNode create(
            UUID eventId,
            long chainSequence,
            AppendAuditEventCommand command,
            Instant recordedAt,
            ProtectedPayload payload,
            String previousHash) {
        return create(
                eventId,
                chainSequence,
                command.eventType(),
                command.actorId(),
                command.resourceType(),
                command.resourceId(),
                recordedAt,
                payload.plaintextCommitment(),
                payload.ciphertextHash(),
                previousHash);
    }

    public ObjectNode create(
            UUID eventId,
            long chainSequence,
            String eventType,
            String actorId,
            String resourceType,
            String resourceId,
            Instant recordedAt,
            String payloadCommitment,
            String payloadCiphertextHash,
            String previousHash) {
        ObjectNode hashInput = objectMapper.createObjectNode();
        hashInput.put("chainSequence", chainSequence);
        hashInput.put("eventId", eventId.toString());
        hashInput.put("eventType", eventType);
        hashInput.put("actorId", actorId);
        hashInput.put("resourceType", resourceType);
        hashInput.put("resourceId", resourceId);
        hashInput.put("recordedAt", recordedAt.toString());
        hashInput.put("payloadCommitment", payloadCommitment);
        hashInput.put("payloadCiphertextHash", payloadCiphertextHash);
        hashInput.put("previousHash", previousHash);
        hashInput.put("hashAlgorithm", hashProperties.algorithm());
        hashInput.put("hashVersion", hashProperties.version());
        return hashInput;
    }
}
