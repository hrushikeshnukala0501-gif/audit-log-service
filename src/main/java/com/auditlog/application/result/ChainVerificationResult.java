package com.auditlog.application.result;

import java.time.Instant;
import java.util.UUID;

public record ChainVerificationResult(
        boolean intact,
        boolean completeChainVerification,
        Long verifiedFromSequence,
        long verifiedThroughSequence,
        Violation violation,
        Instant verifiedAt) {

    public record Violation(UUID eventId, Long chainSequence, String type, String message) {
    }
}
