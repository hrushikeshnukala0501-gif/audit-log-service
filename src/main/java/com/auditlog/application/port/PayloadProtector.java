package com.auditlog.application.port;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Application contract for protecting and recovering audit-event payloads.
 */
public interface PayloadProtector {

    ProtectedPayload protect(JsonNode payload);

    JsonNode unprotect(String algorithm, byte[] nonce, byte[] ciphertext);
}
