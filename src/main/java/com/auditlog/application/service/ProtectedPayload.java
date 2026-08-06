package com.auditlog.application.service;

/**
 * Encrypted representation used for persistence. It never exposes plaintext outside the append transaction.
 */
public record ProtectedPayload(
        String algorithm,
        String keyReference,
        byte[] nonce,
        byte[] ciphertext,
        String plaintextCommitment,
        String ciphertextHash) {
}
