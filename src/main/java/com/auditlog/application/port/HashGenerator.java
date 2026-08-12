package com.auditlog.application.port;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Application contract for calculating and validating integrity hashes.
 */
public interface HashGenerator {

    String hash(JsonNode value);

    String hash(byte[] value);

    boolean isValidHash(String value);
}
