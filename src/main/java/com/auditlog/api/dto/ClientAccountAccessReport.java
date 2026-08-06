package com.auditlog.api.dto;
import java.time.Instant; import java.util.*;
public record ClientAccountAccessReport(String accountId,Instant from,Instant to,Instant generatedAt,String genesisHash,Long firstSequence,Long lastSequence,String predecessorHash,String lastContentHash,String reportHash,List<AccessRecord> records){ public record AccessRecord(UUID eventId,long chainSequence,String eventType,String actorId,Instant recordedAt,String previousHash,String contentHash,String hashAlgorithm,short hashVersion){} }
