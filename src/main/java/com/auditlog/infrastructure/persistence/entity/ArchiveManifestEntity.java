package com.auditlog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "archive_manifest")
public class ArchiveManifestEntity {
    @Id @Column(name = "archive_manifest_id") private UUID id;
    @Column(name = "from_sequence") private long fromSequence;
    @Column(name = "to_sequence") private long toSequence;
    @Column(name = "predecessor_hash") private String predecessorHash;
    @Column(name = "first_content_hash") private String firstContentHash;
    @Column(name = "last_content_hash") private String lastContentHash;
    @Column(name = "archive_uri") private String archiveUri;
    @Column(name = "archive_bundle_hash") private String archiveBundleHash;
    @Column(name = "retention_policy_version") private String retentionPolicyVersion;
    @Column(name = "archived_by") private String archivedBy;
    @Column(name = "archived_at") private Instant archivedAt;
    protected ArchiveManifestEntity() { }
    public ArchiveManifestEntity(UUID id, long fromSequence, long toSequence, String predecessorHash, String firstContentHash, String lastContentHash, String archiveUri, String archiveBundleHash, String retentionPolicyVersion, String archivedBy, Instant archivedAt) { this.id=id; this.fromSequence=fromSequence; this.toSequence=toSequence; this.predecessorHash=predecessorHash; this.firstContentHash=firstContentHash; this.lastContentHash=lastContentHash; this.archiveUri=archiveUri; this.archiveBundleHash=archiveBundleHash; this.retentionPolicyVersion=retentionPolicyVersion; this.archivedBy=archivedBy; this.archivedAt=archivedAt; }
    public UUID getId() { return id; }
    public long getFromSequence() { return fromSequence; }
    public long getToSequence() { return toSequence; }
    public String getArchiveUri() { return archiveUri; }
    public String getArchiveBundleHash() { return archiveBundleHash; }
    public Instant getArchivedAt() { return archivedAt; }
}
