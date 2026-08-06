package com.auditlog.api.dto;

import com.auditlog.api.validation.ValidJsonObject;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Public write contract. The service assigns the authoritative recorded timestamp.
 */
public record CreateAuditEventRequest(
        @NotBlank(message = "eventType is required")
        @Size(max = 100, message = "eventType must not exceed 100 characters")
        String eventType,
        @NotBlank(message = "actorId is required")
        @Size(max = 255, message = "actorId must not exceed 255 characters")
        String actorId,
        @NotBlank(message = "resourceType is required")
        @Size(max = 100, message = "resourceType must not exceed 100 characters")
        String resourceType,
        @NotBlank(message = "resourceId is required")
        @Size(max = 255, message = "resourceId must not exceed 255 characters")
        String resourceId,
        @NotNull(message = "payload is required")
        @ValidJsonObject
        JsonNode payload) {
}
