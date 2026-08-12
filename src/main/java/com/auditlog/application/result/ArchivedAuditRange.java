package com.auditlog.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Application-level outcome of creating a logical archive manifest.
 *
 * <p>This deliberately exposes archive metadata rather than the JPA entity so
 * HTTP adapters do not depend on persistence mappings.</p>
 */
public record ArchivedAuditRange(
        UUID archiveManifestId,
        long fromSequence,
        long toSequence,
        String archiveUri,
        String archiveBundleHash,
        Instant archivedAt) {
}
