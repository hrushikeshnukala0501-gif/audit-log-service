package com.auditlog.application.service;
import com.auditlog.config.AuditRetentionProperties;
import com.auditlog.application.port.HashGenerator;
import com.auditlog.application.result.ArchivedAuditRange;
import com.auditlog.infrastructure.persistence.entity.ArchiveManifestEntity;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.repository.ArchiveManifestRepository;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class RetentionArchiveService {
    private final AuditEventRepository events; private final ArchiveManifestRepository manifests; private final AuditRetentionProperties retention; private final Sha256HashGenerator hashes; private final Clock clock;
    public RetentionArchiveService(AuditEventRepository events, ArchiveManifestRepository manifests, AuditRetentionProperties retention, Sha256HashGenerator hashes, Clock clock) { this.events=events; this.manifests=manifests; this.retention=retention; this.hashes=hashes; this.clock=clock; }
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ArchivedAuditRange archiveEligible(String archivedBy) {
        Instant now=Instant.now(clock);
        long archivedThrough = manifests.findTopByOrderByToSequenceDesc().map(ArchiveManifestEntity::getToSequence).orElse(0L);
        List<AuditEventEntity> eligible=events.findByRecordedAtLessThanEqualOrderByChainSequenceAsc(now.minus(retention.archiveAfter())).stream()
                .filter(event -> event.getChainSequence() > archivedThrough).toList();
        if (eligible.isEmpty()) return null;
        AuditEventEntity first=eligible.getFirst(), last=eligible.getLast();
        String bundleHash=hashes.hash(String.join("\n", eligible.stream().map(AuditEventEntity::getContentHash).toList()).getBytes(StandardCharsets.UTF_8));
        ArchiveManifestEntity manifest = manifests.save(new ArchiveManifestEntity(UUID.randomUUID(), first.getChainSequence(), last.getChainSequence(), first.getPreviousHash(), first.getContentHash(), last.getContentHash(), retention.archiveUriPrefix()+"/"+first.getChainSequence()+"-"+last.getChainSequence(), bundleHash, "v1", archivedBy, now));
        return new ArchivedAuditRange(
                manifest.getId(),
                manifest.getFromSequence(),
                manifest.getToSequence(),
                manifest.getArchiveUri(),
                manifest.getArchiveBundleHash(),
                manifest.getArchivedAt());
    }
}
