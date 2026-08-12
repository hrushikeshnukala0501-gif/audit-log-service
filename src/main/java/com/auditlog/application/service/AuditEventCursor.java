package com.auditlog.application.service;

import com.auditlog.application.query.SortDirection;
import com.auditlog.support.exception.AuditLogException;
import com.auditlog.support.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Versionless, opaque seek cursor for the fixed chain-sequence sort field.
 */
public record AuditEventCursor(long chainSequence, SortDirection sortDirection) {

    public static AuditEventCursor decode(String value, SortDirection expectedDirection) {
        try {
            if (value == null) {
                throw invalidCursor();
            }
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 2) {
                throw invalidCursor();
            }
            SortDirection direction = SortDirection.valueOf(parts[0]);
            long sequence = Long.parseLong(parts[1]);
            if (sequence < 1 || direction != expectedDirection) {
                throw invalidCursor();
            }
            return new AuditEventCursor(sequence, direction);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
    }

    public String encode() {
        String value = sortDirection.name() + ":" + chainSequence;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static AuditLogException invalidCursor() {
        return new AuditLogException(ErrorCode.INVALID_CURSOR, "Cursor is invalid for the requested sort direction");
    }
}
