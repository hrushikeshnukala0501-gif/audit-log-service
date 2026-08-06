package com.auditlog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditExportBundle(String selectorType, String selectorValue, Instant generatedAt, String genesisHash,
                                Long firstSequence, Long lastSequence, String predecessorHash, String lastContentHash,
                                String bundleHash, List<ExportedAuditEvent> events) {
    public record ExportedAuditEvent(UUID eventId, long chainSequence, String eventType, String actorId,
                                     String resourceType, String resourceId, Instant recordedAt,
                                     String payloadCommitment, String payloadCiphertextHash, String previousHash,
                                     String contentHash, String hashAlgorithm, short hashVersion) { }
}
