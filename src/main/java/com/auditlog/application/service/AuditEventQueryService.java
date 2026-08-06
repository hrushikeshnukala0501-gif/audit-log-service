package com.auditlog.application.service;

import com.auditlog.api.dto.AuditEventResponse;
import com.auditlog.application.query.AuditEventSearchCriteria;
import com.auditlog.application.query.SortDirection;
import com.auditlog.application.result.AuditEventPage;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.entity.AuditEventPayloadEntity;
import com.auditlog.infrastructure.persistence.repository.AuditEventPayloadRepository;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import com.auditlog.support.exception.AuditLogException;
import com.auditlog.support.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Query use case using fixed-field, keyset pagination. It never mutates audit history.
 */
@Service
public class AuditEventQueryService {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventPayloadRepository payloadRepository;
    private final AesGcmPayloadProtector payloadProtector;

    public AuditEventQueryService(
            AuditEventRepository auditEventRepository,
            AuditEventPayloadRepository payloadRepository,
            AesGcmPayloadProtector payloadProtector) {
        this.auditEventRepository = auditEventRepository;
        this.payloadRepository = payloadRepository;
        this.payloadProtector = payloadProtector;
    }

    @Transactional(readOnly = true)
    public AuditEventPage find(AuditEventSearchCriteria criteria) {
        validateTimeRange(criteria);
        AuditEventCursor cursor = criteria.cursor() == null ? null : AuditEventCursor.decode(criteria.cursor(), criteria.sortDirection());
        int requestedSize = criteria.pageSize() + 1;
        Sort.Direction direction = criteria.sortDirection() == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        List<AuditEventEntity> fetched = auditEventRepository.findAll(
                AuditEventSpecifications.from(criteria, cursor),
                PageRequest.of(0, requestedSize, Sort.by(direction, "chainSequence"))).getContent();

        boolean hasNextPage = fetched.size() > criteria.pageSize();
        List<AuditEventEntity> pageEvents = hasNextPage ? fetched.subList(0, criteria.pageSize()) : fetched;
        Map<UUID, AuditEventPayloadEntity> payloadsByEventId = payloadsByEventId(pageEvents);
        List<AuditEventResponse> events = pageEvents.stream()
                .map(event -> toResponse(event, payloadsByEventId.get(event.getEventId())))
                .toList();
        String nextCursor = hasNextPage
                ? new AuditEventCursor(pageEvents.getLast().getChainSequence(), criteria.sortDirection()).encode()
                : null;
        return new AuditEventPage(events, nextCursor);
    }

    private void validateTimeRange(AuditEventSearchCriteria criteria) {
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new AuditLogException(ErrorCode.MALFORMED_REQUEST, "from must be before or equal to to");
        }
        if (criteria.pageSize() < 1 || criteria.pageSize() > 100) {
            throw new AuditLogException(ErrorCode.MALFORMED_REQUEST, "pageSize must be between 1 and 100");
        }
        if (criteria.sortDirection() == null) {
            throw new AuditLogException(ErrorCode.MALFORMED_REQUEST, "sortDirection is required");
        }
    }

    private Map<UUID, AuditEventPayloadEntity> payloadsByEventId(List<AuditEventEntity> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AuditEventPayloadEntity> payloads = new HashMap<>();
        payloadRepository.findByEventIdIn(events.stream().map(AuditEventEntity::getEventId).toList())
                .forEach(payload -> payloads.put(payload.getEventId(), payload));
        return payloads;
    }

    private AuditEventResponse toResponse(AuditEventEntity event, AuditEventPayloadEntity payload) {
        if (payload == null) {
            throw new AuditLogException(ErrorCode.CHAIN_INTEGRITY_VIOLATION,
                    "Audit event payload is missing for event " + event.getEventId());
        }
        return new AuditEventResponse(
                event.getEventId(),
                event.getChainSequence(),
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getRecordedAt(),
                payloadProtector.unprotect(payload.getEncryptionAlgorithm(), payload.getEncryptionNonce(), payload.getCiphertext()),
                event.getPreviousHash(),
                event.getContentHash(),
                event.getHashAlgorithm(),
                event.getHashVersion());
    }
}
