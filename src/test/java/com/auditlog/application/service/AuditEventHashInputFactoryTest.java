package com.auditlog.application.service;

import com.auditlog.config.AuditHashProperties;
import com.auditlog.support.utility.CanonicalJsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventHashInputFactoryTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonSerializer canonicalJson = new CanonicalJsonSerializer(objectMapper);
    private final AuditEventHashInputFactory factory = new AuditEventHashInputFactory(
            objectMapper,
            new AuditHashProperties("SHA-256", HASH, (short) 1));

    @Test
    void createsDistinctNamedHashInputsForSeparatorAmbiguousFieldValues() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant recordedAt = Instant.parse("2026-01-01T00:00:00Z");

        byte[] first = canonicalJson.serialize(factory.create(
                eventId, 1, "EVENT", "actor|resource", "type", "id", recordedAt, HASH, HASH, HASH));
        byte[] second = canonicalJson.serialize(factory.create(
                eventId, 1, "EVENT", "actor", "resource|type", "id", recordedAt, HASH, HASH, HASH));

        assertThat(first).isNotEqualTo(second);
    }
}
