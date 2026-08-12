package com.auditlog.application.port;

/**
 * Encrypted payload material returned by a {@link PayloadProtector} for persistence.
 */
public record ProtectedPayload(
        String algorithm,
        String keyReference,
        byte[] nonce,
        byte[] ciphertext,
        String plaintextCommitment,
        String ciphertextHash) {
}
