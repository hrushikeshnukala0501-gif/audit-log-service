package com.auditlog.application.service;

import com.auditlog.api.dto.AuditExportBundle;
import com.auditlog.config.AuditHashProperties;
import com.auditlog.infrastructure.persistence.entity.AuditEventEntity;
import com.auditlog.infrastructure.persistence.repository.AuditEventRepository;
import com.auditlog.support.exception.AuditLogException;
import com.auditlog.support.exception.ErrorCode;
import com.auditlog.support.utility.CanonicalJsonSerializer;
import com.auditlog.support.utility.Sha256HashGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class AuditExportService {
    private final AuditEventRepository events; private final AuditHashProperties hashes; private final Sha256HashGenerator sha256; private final CanonicalJsonSerializer canonicalJson; private final ObjectMapper json; private final Clock clock;
    public AuditExportService(AuditEventRepository events, AuditHashProperties hashes, Sha256HashGenerator sha256, CanonicalJsonSerializer canonicalJson, ObjectMapper json, Clock clock) { this.events=events; this.hashes=hashes; this.sha256=sha256; this.canonicalJson=canonicalJson; this.json=json; this.clock=clock; }
    @Transactional(readOnly=true)
    public AuditExportBundle export(String actorId,String resourceId) {
        boolean actor=actorId!=null&&!actorId.isBlank(), resource=resourceId!=null&&!resourceId.isBlank();
        if(actor==resource) throw new AuditLogException(ErrorCode.MALFORMED_REQUEST,"Specify exactly one of actorId or resourceId");
        List<AuditExportBundle.ExportedAuditEvent> result=(actor?events.findByActorIdOrderByChainSequenceAsc(actorId):events.findByResourceIdOrderByChainSequenceAsc(resourceId)).stream().map(this::map).toList();
        Long first=result.isEmpty()?null:result.getFirst().chainSequence(), last=result.isEmpty()?null:result.getLast().chainSequence();
        String predecessor=result.isEmpty()?hashes.genesisHash():result.getFirst().previousHash(), lastHash=result.isEmpty()?hashes.genesisHash():result.getLast().contentHash();
        String bundleHash=sha256.hash(canonicalJson.serialize(json.valueToTree(result)));
        return new AuditExportBundle(actor?"actorId":"resourceId",actor?actorId:resourceId,Instant.now(clock),hashes.genesisHash(),first,last,predecessor,lastHash,bundleHash,result);
    }
    private AuditExportBundle.ExportedAuditEvent map(AuditEventEntity e){ return new AuditExportBundle.ExportedAuditEvent(e.getEventId(),e.getChainSequence(),e.getEventType(),e.getActorId(),e.getResourceType(),e.getResourceId(),e.getRecordedAt(),e.getPayloadCommitment(),e.getPayloadCiphertextHash(),e.getPreviousHash(),e.getContentHash(),e.getHashAlgorithm(),e.getHashVersion()); }
}
