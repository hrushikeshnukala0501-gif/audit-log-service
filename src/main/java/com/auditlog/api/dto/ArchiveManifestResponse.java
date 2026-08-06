package com.auditlog.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ArchiveManifestResponse(UUID archiveManifestId, long fromSequence, long toSequence,
                                      String archiveUri, String archiveBundleHash, Instant archivedAt) { }
