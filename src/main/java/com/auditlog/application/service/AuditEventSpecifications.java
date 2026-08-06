package com.auditlog.application.service;

import com.auditlog.application.query.AuditEventSearchCriteria;
import com.auditlog.application.query.SortDirection;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    static Specification<AuditEventEntity> from(AuditEventSearchCriteria criteria, AuditEventCursor cursor) {
        return Specification.allOf(
                equalIfPresent("actorId", criteria.actorId()),
                equalIfPresent("resourceType", criteria.resourceType()),
                equalIfPresent("resourceId", criteria.resourceId()),
                equalIfPresent("eventType", criteria.eventType()),
                fromIfPresent(criteria.from()),
                toIfPresent(criteria.to()),
                afterCursor(cursor));
    }

    private static Specification<AuditEventEntity> equalIfPresent(String field, String value) {
        return (root, query, builder) -> value == null ? null : builder.equal(root.get(field), value);
    }

    private static Specification<AuditEventEntity> fromIfPresent(Instant from) {
        return (root, query, builder) -> from == null ? null : builder.greaterThanOrEqualTo(root.get("recordedAt"), from);
    }

    private static Specification<AuditEventEntity> toIfPresent(Instant to) {
        return (root, query, builder) -> to == null ? null : builder.lessThanOrEqualTo(root.get("recordedAt"), to);
    }

    private static Specification<AuditEventEntity> afterCursor(AuditEventCursor cursor) {
        return (root, query, builder) -> {
            if (cursor == null) {
                return null;
            }
            return cursor.sortDirection() == SortDirection.ASC
                    ? builder.greaterThan(root.get("chainSequence"), cursor.chainSequence())
                    : builder.lessThan(root.get("chainSequence"), cursor.chainSequence());
        };
    }
}
