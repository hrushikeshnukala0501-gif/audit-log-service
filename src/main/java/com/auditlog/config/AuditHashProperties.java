package com.auditlog.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Versioned integrity settings. Changing any value requires an explicit data migration strategy.
 */
@Validated
@ConfigurationProperties(prefix = "audit.hash")
public record AuditHashProperties(
        @NotBlank
        @Pattern(regexp = "SHA-256", message = "must be SHA-256")
        String algorithm,
        @NotBlank
        @Pattern(regexp = "^[0-9a-f]{64}$", message = "must be a lower-case SHA-256 hexadecimal value")
        String genesisHash,
        @Min(value = 1, message = "must be positive")
        short version) {
}
