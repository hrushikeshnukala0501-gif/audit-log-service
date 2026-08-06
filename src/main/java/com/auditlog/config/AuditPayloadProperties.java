package com.auditlog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Base64;

/**
 * Payload encryption configuration. The key is supplied by a secret manager or environment variable.
 */
@Validated
@ConfigurationProperties(prefix = "audit.payload")
public record AuditPayloadProperties(
        @NotBlank
        @Pattern(regexp = "AES/GCM/NoPadding", message = "must be AES/GCM/NoPadding")
        String algorithm,
        @NotBlank String keyReference,
        @NotBlank String base64Key) {

    @AssertTrue(message = "base64Key must be a Base64-encoded 256-bit (32-byte) AES key")
    public boolean isValidAes256Key() {
        try {
            return Base64.getDecoder().decode(base64Key).length == 32;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
