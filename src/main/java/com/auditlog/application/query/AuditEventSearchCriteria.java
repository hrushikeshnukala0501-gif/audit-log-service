package com.auditlog.application.query;

import java.time.Instant;

/**
 * Validated application query model. The cursor is opaque to API callers.
 */
public record AuditEventSearchCriteria(
        String actorId,
        String resourceType,
        String resourceId,
        String eventType,
        Instant from,
        Instant to,
        String cursor,
        int pageSize,
        SortDirection sortDirection) {
}
