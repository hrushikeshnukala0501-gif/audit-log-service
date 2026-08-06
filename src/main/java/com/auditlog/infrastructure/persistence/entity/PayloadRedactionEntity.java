package com.auditlog.infrastructure.persistence.entity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="payload_redaction") public class PayloadRedactionEntity {
 @Id @Column(name="redaction_id") private UUID id; @Column(name="target_event_id") private UUID targetEventId; @Column(name="redaction_event_id") private UUID redactionEventId; @Column(name="json_pointer") private String jsonPointer; @Column(name="redaction_reason") private String reason; @Column(name="policy_version") private String policyVersion; @Column(name="authorized_by") private String authorizedBy; @Column(name="redacted_at") private Instant redactedAt; @Column(name="key_destruction_reference") private String keyReference;
 protected PayloadRedactionEntity(){} public PayloadRedactionEntity(UUID id,UUID targetEventId,UUID redactionEventId,String jsonPointer,String reason,String policyVersion,String authorizedBy,Instant redactedAt,String keyReference){this.id=id;this.targetEventId=targetEventId;this.redactionEventId=redactionEventId;this.jsonPointer=jsonPointer;this.reason=reason;this.policyVersion=policyVersion;this.authorizedBy=authorizedBy;this.redactedAt=redactedAt;this.keyReference=keyReference;}
 public UUID getTargetEventId(){return targetEventId;} public String getJsonPointer(){return jsonPointer;}
}
