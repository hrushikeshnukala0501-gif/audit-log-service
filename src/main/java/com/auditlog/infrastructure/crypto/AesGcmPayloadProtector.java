package com.auditlog.infrastructure.crypto;

import com.auditlog.application.port.HashGenerator;
import com.auditlog.application.port.PayloadProtectionException;
import com.auditlog.application.port.PayloadProtector;
import com.auditlog.application.port.ProtectedPayload;
import com.auditlog.config.AuditPayloadProperties;
import com.auditlog.support.utility.CanonicalJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Local AES-GCM implementation of the application payload-protection contract.
 * Keys are supplied externally and are never persisted.
 */
@Component
public class AesGcmPayloadProtector implements PayloadProtector {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_NONCE_LENGTH_BYTES = 12;

    private final AuditPayloadProperties properties;
    private final CanonicalJsonSerializer canonicalJsonSerializer;
    private final HashGenerator hashGenerator;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmPayloadProtector(
            AuditPayloadProperties properties,
            CanonicalJsonSerializer canonicalJsonSerializer,
            HashGenerator hashGenerator,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.canonicalJsonSerializer = canonicalJsonSerializer;
        this.hashGenerator = hashGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProtectedPayload protect(JsonNode payload) {
        byte[] plaintext = canonicalJsonSerializer.serialize(payload);
        byte[] nonce = new byte[GCM_NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        byte[] ciphertext = encrypt(plaintext, nonce);
        return new ProtectedPayload(
                properties.algorithm(),
                properties.keyReference(),
                nonce,
                ciphertext,
                hashGenerator.hash(plaintext),
                hashGenerator.hash(ciphertext));
    }

    @Override
    public JsonNode unprotect(String algorithm, byte[] nonce, byte[] ciphertext) {
        if (!properties.algorithm().equals(algorithm)) {
            throw new PayloadProtectionException("Unsupported audit payload encryption algorithm", null);
        }
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return objectMapper.readTree(cipher.doFinal(ciphertext));
        } catch (GeneralSecurityException | IllegalArgumentException | java.io.IOException exception) {
            throw new PayloadProtectionException("Unable to decrypt audit payload", exception);
        }
    }

    private byte[] encrypt(byte[] plaintext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance(properties.algorithm());
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new PayloadProtectionException("Unable to protect audit payload", exception);
        }
    }

    private SecretKey encryptionKey() {
        byte[] key = Base64.getDecoder().decode(properties.base64Key());
        if (key.length != 32) {
            throw new PayloadProtectionException("Audit payload encryption key must be a 256-bit Base64 value", null);
        }
        return new SecretKeySpec(key, "AES");
    }
}
