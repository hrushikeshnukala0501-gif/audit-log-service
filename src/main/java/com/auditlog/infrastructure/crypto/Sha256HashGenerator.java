package com.auditlog.infrastructure.crypto;

import com.auditlog.application.port.HashGenerator;
import com.auditlog.support.constant.AuditHashConstants;
import com.auditlog.support.utility.CanonicalJsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Local SHA-256 implementation of the application integrity-hash contract.
 */
@Component
public class Sha256HashGenerator implements HashGenerator {

    private final CanonicalJsonSerializer canonicalJsonSerializer;

    public Sha256HashGenerator(CanonicalJsonSerializer canonicalJsonSerializer) {
        this.canonicalJsonSerializer = canonicalJsonSerializer;
    }

    @Override
    public String hash(JsonNode value) {
        return hash(canonicalJsonSerializer.serialize(value));
    }

    @Override
    public String hash(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(AuditHashConstants.SHA_256);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in a Java runtime", exception);
        }
    }

    @Override
    public boolean isValidHash(String value) {
        return value != null && value.matches("^[0-9a-f]{" + AuditHashConstants.SHA_256_HEX_LENGTH + "}$");
    }
}
