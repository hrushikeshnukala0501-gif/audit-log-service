package com.auditlog.support.utility;

import com.auditlog.support.constant.AuditHashConstants;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stateless SHA-256 helper. A new MessageDigest is created per call because it is not thread-safe.
 */
@Component
public class Sha256HashGenerator {

    private final CanonicalJsonSerializer canonicalJsonSerializer;

    public Sha256HashGenerator(CanonicalJsonSerializer canonicalJsonSerializer) {
        this.canonicalJsonSerializer = canonicalJsonSerializer;
    }

    public String hash(JsonNode value) {
        return hash(canonicalJsonSerializer.serialize(value));
    }

    public String hash(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(AuditHashConstants.SHA_256);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in a Java runtime", exception);
        }
    }

    public boolean isValidHash(String value) {
        return value != null && value.matches("^[0-9a-f]{" + AuditHashConstants.SHA_256_HEX_LENGTH + "}$");
    }
}
