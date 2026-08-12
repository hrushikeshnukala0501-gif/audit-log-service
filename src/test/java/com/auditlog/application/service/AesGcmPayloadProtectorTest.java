package com.auditlog.application.service;

import com.auditlog.config.AuditPayloadProperties;
import com.auditlog.support.utility.CanonicalJsonSerializer;
import com.auditlog.support.utility.Sha256HashGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmPayloadProtectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonSerializer canonicalJson = new CanonicalJsonSerializer(objectMapper);
    private final AesGcmPayloadProtector protector = new AesGcmPayloadProtector(
            new AuditPayloadProperties(
                    "AES/GCM/NoPadding",
                    "test-key",
                    "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="),
            canonicalJson,
            new Sha256HashGenerator(canonicalJson),
            objectMapper);

    @Test
    void generatesDifferentNoncesForTwoEncryptionsOfTheSamePayload() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"account\":\"account-1\"}");

        ProtectedPayload first = protector.protect(payload);
        ProtectedPayload second = protector.protect(payload);

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void rejectsCiphertextWhoseAuthenticationTagHasBeenChanged() throws Exception {
        ProtectedPayload protectedPayload = protector.protect(objectMapper.readTree("{\"secret\":\"value\"}"));
        byte[] tamperedCiphertext = protectedPayload.ciphertext();
        tamperedCiphertext[tamperedCiphertext.length - 1] ^= 1;

        assertThatThrownBy(() -> protector.unprotect(
                protectedPayload.algorithm(), protectedPayload.nonce(), tamperedCiphertext))
                .isInstanceOf(PayloadProtectionException.class);
    }
}