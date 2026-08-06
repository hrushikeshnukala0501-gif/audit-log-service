package com.auditlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the Audit Log Service application.
 *
 * <p>The root package intentionally sits above API, application, domain, and
 * infrastructure packages+++++++++ so Spring component scanning has one explicit boundary.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuditLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditLogApplication.class, args);
    }
}
