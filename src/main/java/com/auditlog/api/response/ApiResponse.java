package com.auditlog.api.response;

import java.time.Instant;

/**
 * Uniform envelope for successful public API responses.
 */
public record ApiResponse<T>(Instant timestamp, String traceId, T data) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(Instant.now(), traceId, data);
    }
}
