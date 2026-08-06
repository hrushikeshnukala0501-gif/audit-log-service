package com.auditlog.application.result;

import java.time.Instant;
import java.util.UUID;

public record AppendedAuditEvent(
        UUID eventId,
        long chainSequence,
        Instant recordedAt,
        String previousHash,
        String contentHash,
        String hashAlgorithm,
        short hashVersion) {
}
