package com.auditlog.application.command;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Application-layer write command, deliberately independent of the HTTP request DTO.
 */
public record AppendAuditEventCommand(
        String eventType,
        String actorId,
        String resourceType,
        String resourceId,
        JsonNode payload) {
}
