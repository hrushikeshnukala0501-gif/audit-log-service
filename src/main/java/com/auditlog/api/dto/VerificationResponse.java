package com.auditlog.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(
        boolean intact,
        long verifiedThroughSequence,
        VerificationViolation violation,
        Instant verifiedAt) {

    public record VerificationViolation(
            UUID eventId,
            Long chainSequence,
            String type,
            String message) {
    }
}
