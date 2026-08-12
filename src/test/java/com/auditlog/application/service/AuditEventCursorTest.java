package com.auditlog.application.service;

import com.auditlog.application.query.SortDirection;
import com.auditlog.support.exception.AuditLogException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventCursorTest {

    @Test
    void roundTripsSequenceAndSortDirection() {
        AuditEventCursor original = new AuditEventCursor(42, SortDirection.DESC);

        assertThat(AuditEventCursor.decode(original.encode(), SortDirection.DESC)).isEqualTo(original);
    }

    @Test
    void rejectsNullMalformedAndInvalidCursorValues() {
        assertInvalid(null, SortDirection.ASC);
        assertInvalid("not-base64", SortDirection.ASC);
        assertInvalid("QVND", SortDirection.ASC);
        assertInvalid("Tk9QRTox", SortDirection.ASC);
        assertInvalid("QVNDOnR3bw", SortDirection.ASC);
        assertInvalid("QVNDOjA", SortDirection.ASC);
    }

    @Test
    void rejectsCursorForDifferentSortDirection() {
        assertInvalid(new AuditEventCursor(1, SortDirection.ASC).encode(), SortDirection.DESC);
    }

    private void assertInvalid(String value, SortDirection direction) {
        assertThatThrownBy(() -> AuditEventCursor.decode(value, direction))
                .isInstanceOf(AuditLogException.class)
                .hasMessage("Cursor is invalid for the requested sort direction");
    }
}
