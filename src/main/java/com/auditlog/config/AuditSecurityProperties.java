package com.auditlog.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Secret-backed credential for service-to-service API access.
 */
@Validated
@ConfigurationProperties(prefix = "audit.security")
public record AuditSecurityProperties(@NotBlank String apiKey) {
}
