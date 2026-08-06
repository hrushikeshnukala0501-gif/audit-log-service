package com.auditlog.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID eventId,
        long chainSequence,
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        Instant recordedAt,
        JsonNode payload,
        String previousHash,
        String contentHash,
        String hashAlgorithm,
        short hashVersion) {
}
