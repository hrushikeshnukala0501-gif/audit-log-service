# Scenario B - Retention, Redaction, and Export

## Retention

`POST /api/v1/audit/retention/archive` creates an immutable `archive_manifest` for the next contiguous prefix older than `audit.retention.archive-after`. The operation is serializable and starts after the highest previously archived sequence, so repeated calls cannot overlap archive ranges. The prototype performs logical archival: source records remain in H2 and chain verification is unchanged. Moving encrypted bundles to immutable external storage is intentionally deferred.

## Structured redaction

`POST /api/v1/audit/events/{eventId}/redactions` accepts a JSON Pointer, reason, policy version, and authorizer. It appends a `PAYLOAD_REDACTED` audit event and stores immutable redaction evidence. Query projections replace the selected field with `[REDACTED]`; the original ciphertext, plaintext commitment, and original event hash remain unchanged, so verification continues to prove the original record.

This is projection redaction, not cryptographic erasure. The prototype uses one externally supplied AES key for all payloads, so it cannot safely destroy a key for one field. A production implementation needs per-event/per-field envelope keys held by a KMS, authorisation for the redaction route, legal-hold checks, and a non-recoverable key-destruction workflow.

## Bulk export

`GET /api/v1/audit/export` requires exactly one selector: `actorId` or `resourceId`. It returns selected events ordered by immutable sequence, including event identifiers, protected hash inputs, predecessor/content hashes, hash scheme data, global genesis hash, first/last boundaries, and a SHA-256 digest of the canonical exported event list. Recipients can recalculate an event content hash and verify the bundle digest without receiving plaintext payloads.

For non-contiguous selected events, predecessor hashes describe their global chain position but do not prove every omitted intermediate link. A stronger production export should include the intervening chain segment or a trusted signed checkpoint.
