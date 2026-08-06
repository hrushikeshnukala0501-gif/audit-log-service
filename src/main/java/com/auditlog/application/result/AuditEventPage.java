package com.auditlog.application.result;

import com.auditlog.api.dto.AuditEventResponse;

import java.util.List;

public record AuditEventPage(List<AuditEventResponse> events, String nextCursor) {
}
