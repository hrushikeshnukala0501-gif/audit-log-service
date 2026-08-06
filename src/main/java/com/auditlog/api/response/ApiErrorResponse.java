package com.auditlog.api.response;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error envelope that intentionally contains no request payload data.
 */
public record ApiErrorResponse(
        Instant timestamp,
        String traceId,
        int status,
        String code,
        String message,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }
}
