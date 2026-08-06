# Engineering Requirements Analysis - Tamper-Evident Audit Log Service

## 1. Purpose and scope

This document translates the interview assignment into an implementable and testable engineering scope. It distinguishes assignment requirements from proposed design decisions so that implementation choices remain reviewable.

The service records a durable, append-only history of audit events. It must make modification of recorded history detectable through a cryptographic hash chain. The assignment includes three scenarios:

- **Scenario A:** core audit log service - the first working vertical slice and the system foundation.
- **Scenario B:** retention, privacy-preserving redaction, and verifiable export - an extension of Scenario A.
- **Scenario C:** an intentionally ambiguous compliance-reporting request - a requirements-clarification and scoped-design exercise.

## 2. Functional requirements

### 2.1 Event ingestion (Scenario A)

The service shall expose a write API that accepts an audit event with, at minimum:

- `eventType` - the action that occurred.
- `actorId` - the identity that performed or initiated the action.
- `resourceType` - the category of affected resource.
- `resourceId` - the identifier of the affected resource.
- `payload` - structured, event-specific detail.
- `timestamp` - the time associated with the event; the implementation must document whether it is caller-supplied, server-assigned, or both.

Each accepted event shall be stored as a new record. The public API shall not expose event update or delete operations.

### 2.2 Event query (Scenario A)

The service shall expose a query API that retrieves events and supports any combination of these filters:

- `actorId`
- `resourceType` and `resourceId`
- `eventType`
- a `from` and/or `to` time range

The query API shall paginate results for large result sets.

### 2.3 Tamper evidence (Scenario A)

Every stored record shall contain:

- A cryptographic hash derived from the record's protected content.
- The hash of the immediately preceding record, or a documented genesis value for the first record.

These fields shall form an ordered hash chain. A modification to a historical record must be detectable when the chain is verified.

### 2.4 Chain verification (Scenario A)

The service shall expose `GET /audit/verify`. It shall walk the full chain and report:

- Whether the chain is intact.
- If it is not intact, the first inconsistent record.
- The detected violation type.

The delivered validation must demonstrate normal writes and verification, then alter a persisted record directly and demonstrate that verification detects the alteration.

### 2.5 Retention and redaction (Scenario B)

The service shall support a configurable retention window under which older records may be archived or soft-deleted. Chain verification shall distinguish valid, policy-authorized archival from tampering.

The service shall support redaction of selected sensitive payload fields without invalidating the evidence that the original record existed and was part of the chain. The chosen scheme, trade-offs, and limitations must be documented.

### 2.6 Verifiable export (Scenario B)

The service shall provide an export endpoint for all records associated with one `resourceId` or one `actorId`. The bundle shall contain enough chain metadata for a recipient to independently verify that exported records have not changed since export.

### 2.7 Compliance reporting (Scenario C)

For the product statement, "Regulators need to be able to audit access to client account data," the submission shall document:

- Clarifying questions and identified ambiguities.
- Assumptions used where answers are unavailable.
- A clarified requirement statement.
- Resulting technical design.
- Implementation scope and deliberate exclusions, with rationale.

## 3. Non-functional and delivery requirements

| Area | Requirement | Evidence expected |
| --- | --- | --- |
| Correctness | The prototype must be runnable end-to-end and validated through its APIs. | Repeatable setup and tests, including a direct datastore-tampering demonstration. |
| Maintainability | Produce clean, production-quality code, API/schema definitions, tests, and documentation. | Clear structure, reviewed design decisions, and readable tests. |
| Traceability | Demonstrate task decomposition, dependencies, disciplined AI-assisted execution, and engineer ownership. | Incremental commits, AI usage log, plan, and rationale for accepted, edited, or rejected AI output. |
| Security | Use AI safely; apply appropriate security review and require human sign-off for high-impact changes. | No secrets in prompts/repository; documented review and threat considerations. |
| Quality control | Apply analysis, linting, tests, security, and performance quality gates where applicable. | Commands/results recorded in documentation or CI. |
| Communication | Supply architecture, setup, testing, limitations, assumptions, risks/trade-offs, and final summary. | Repository documentation. |
| Defensibility | The candidate must explain the implementation and make a small live change. | Design decisions that are understandable, intentionally scoped, and supported by tests. |

## 4. Ambiguities and clarification questions

| Topic | Missing information / question | Proposed resolution for the prototype |
| --- | --- | --- |
| Timestamp meaning | Is the timestamp the time an action happened, reached the service, or was persisted? Can a caller supply it? | Use a server-assigned UTC `recordedAt` as the protected audit time. If business occurrence time is needed, add a separately labelled caller field with validation. |
| Event identity and ordering | What establishes the immutable order of concurrent writes? | Assign a monotonically increasing database sequence in one append transaction and chain by that sequence. |
| Chain scope | Is the chain global, per tenant, per resource, or per actor? | Start with one global chain; document that multi-tenant or per-stream chains require a distinct design. |
| Hash input | Which fields are protected, how is JSON canonicalized, and how are nulls/Unicode/numbers represented? | Define a versioned canonical serialization, include all protected event fields plus sequence and previous hash, and test deterministic output. |
| Hash algorithm and encoding | Which algorithm, output encoding, and genesis value are expected? | Use SHA-256 with lower-case hexadecimal encoding and a documented constant genesis value; version the scheme. |
| Payload constraints | Are arbitrary JSON payloads valid? Are schemas, size limits, or prohibited fields required? | Accept JSON objects initially, cap request/payload size, and defer event-specific schemas unless supplied. |
| Query semantics | Are filter values exact, case-sensitive, inclusive at time boundaries, and how are null/invalid ranges handled? | Define exact matching and inclusive UTC boundaries; reject malformed dates and `from > to`. |
| Pagination contract | Offset or cursor? What maximum page size and ordering are required? | Use stable cursor pagination ordered by sequence ascending, with a documented bounded page size. |
| Direct datastore access | What datastore will reviewers modify and what is the supported demonstration method? | Provide local database setup and a documented test or SQL script that changes a stored field. |
| Integrity threat model | Must the design resist an administrator rewriting all rows and recomputing hashes? | State explicitly that an internal hash chain detects uncoordinated alteration but not a privileged actor who can rewrite the complete chain. Consider external checkpoints as a future control. |
| Retention semantics | Does "archivable or soft-deletable" mean hide content, move rows, or remove them? Is metadata retained? | Choose one method before implementation; retain an immutable, verifiable continuity record for any omitted content. |
| Redaction authority | Who may redact, which paths are eligible, can redaction be reversed, and must original values ever be recoverable? | Restrict to explicit JSON paths, record a redaction event/proof, and define authorization as a prerequisite. Do not make redacted plaintext recoverable by default. |
| Export proof | Does an export need to prove a contiguous chain segment, inclusion in a global chain, or only internal consistency? | Include event hashes, predecessor hashes, sequence values, chain version, and boundary proofs/anchors sufficient to validate the exported segment. |
| Scenario C policy | Which account data, actors, regulators, report format, access controls, and retention periods apply? | Treat as an analysis-first scope; obtain answers or clearly label assumptions before implementation. |

## 5. Assumptions to document

1. The initial prototype is a single logical tenant and uses a global, sequence-ordered chain.
2. The server assigns the authoritative audit timestamp in UTC; client-provided event time, if supported, is distinct.
3. Events are immutable. A business correction is recorded as a new event, never an in-place change.
4. The first record uses a named, fixed genesis hash and every event carries a hash-scheme version.
5. SHA-256 is sufficient for the prototype's integrity checks, but a chain alone does not prevent a database owner from rewriting the entire chain and recomputing hashes.
6. The database supplies durable transactions and an immutable sequence. Concurrency control will serialize chain appends correctly.
7. Query results have a deterministic sequence order and pagination does not duplicate or skip records under concurrent appends.
8. API authentication and authorization are either implemented as a prototype boundary or explicitly excluded; unauthenticated write access is not production-ready.
9. Audit payloads may contain sensitive information. Logging, error responses, exports, and AI prompts must not expose secrets or real personal data.
10. Retention/redaction policy, legal hold, key management, and regulator-specific obligations are not yet supplied and cannot be claimed as compliant without stakeholder confirmation.

## 6. Risks and edge cases

| Category | Risk or edge case | Required treatment |
| --- | --- | --- |
| Integrity | A stored event field changes but its stored event hash remains unchanged. | Recalculate the content hash and report a content-hash mismatch at that sequence. |
| Integrity | A stored event hash changes, or its predecessor hash points to the wrong record. | Validate both recalculated content hash and expected predecessor linkage; classify the first failure. |
| Integrity | A row is deleted, inserted, or reordered directly in storage. | Verify contiguous sequence/order and predecessor linkage; report the earliest detectable gap or link violation. |
| Integrity | An attacker changes all downstream hashes after changing historical content. | Document this limitation; mitigate in future through signed/external checkpoints or write-once storage. |
| Concurrency | Two writes read the same predecessor and create competing links. | Use a transaction/lock/serialization strategy and add concurrent-write tests. |
| Canonicalization | JSON key order, number formatting, Unicode normalization, omitted versus null fields, or time precision changes the same logical event's hash. | Specify canonical representation precisely and test each case. |
| Availability | Verification is expensive on a large chain. | Define expected prototype scale; consider pagination/checkpoints or asynchronous validation for production. |
| Query correctness | Time-zone conversions, inclusive boundaries, empty filters, invalid ranges, and page cursors produce surprising results. | Validate inputs and cover boundary cases in integration tests. |
| Privacy | Sensitive payload data leaks through logs, error messages, exports, or a naive redaction implementation. | Minimize logging, protect export access, and design redaction before exposing it. |
| Retention | Removing an archived record makes the remaining predecessor link look tampered. | Preserve verifiable continuity metadata or define a signed archival boundary. |
| Export | A filtered export is internally consistent but cannot be tied to the source global chain. | Include boundary proofs and chain version/algorithm metadata. |
| Operations | Database backup/restore, migration, or clock change affects sequence, integrity, or time interpretation. | Document backup/migration procedures and verify chain after restoration. |

## 7. Acceptance criteria

### AC-1: append an audit event

- Given a request with all required fields and a JSON-object payload, when it is valid, then exactly one event is persisted and returned with an immutable identifier/sequence, authoritative timestamp, content hash, predecessor hash, and hash-scheme version.
- Given a missing, blank, malformed, or oversized required field, when submitted, then the service returns a documented validation error and persists no event.
- Given any accepted write, then no existing event is changed.
- The public API specification contains no update or delete operation for audit events.

### AC-2: query audit events

- Given stored events, when any supported filter combination is used, then only matching events are returned.
- Given valid `from` and `to` values, then the documented time-boundary semantics are applied consistently.
- Given `from > to`, malformed time values, an invalid cursor, or an out-of-range page size, then the service returns a documented validation error.
- Given more matches than one page, then consecutive page requests return a complete, stable, non-duplicated sequence and a next cursor only when further results exist.

### AC-3: construct a hash chain

- The first event has the documented genesis predecessor value.
- Each later event contains the immediately prior event's content hash as its predecessor hash.
- Recomputing a stored event's hash from the documented canonical input produces its stored content hash.
- Canonicalization is deterministic across JSON field order and supported temporal/numeric representations.

### AC-4: verify the chain

- Given only normally appended events, `GET /audit/verify` reports the chain intact.
- Given a directly modified protected field, stored hash, predecessor hash, sequence, or deleted record, verification reports not intact, the earliest inconsistent record it can identify, and a violation type.
- Verification does not stop at a later symptom when an earlier inconsistency is detectable.
- The demonstration can be run locally from documented setup steps without an external consumer.

### AC-5: retention and archival

- Given a record older than configured retention and a valid retention action, archival/soft deletion follows the documented policy.
- Given legitimately archived records, verification remains valid according to the documented continuity model.
- Given arbitrary removal falsely presented as archival, verification or archival metadata checks identify the discrepancy.

### AC-6: redaction

- Given an authorized redaction request for an allowed payload path, the sensitive plaintext is no longer returned where policy requires it.
- The service preserves verifiable evidence of the original event and redaction action without silently rewriting the original chain.
- Given an unauthorized path/request or an attempt to redact protected chain metadata, the action is rejected and no data changes.
- Documentation explains why the design meets the chosen privacy and integrity goals and where it does not.

### AC-7: verifiable export

- Given an actor or resource filter, the export includes all matching events and the chain metadata necessary for the documented independent-verification method.
- A recipient can validate the bundle without calling the source service.
- Altering an exported protected event or proof causes independent verification to fail.
- An empty result has a documented representation and verification result.

### AC-8: compliance-reporting scope

- The clarified requirement identifies the regulated data, audited actions, actor population, reporting audience, access control, report contents, retention, and auditability needs.
- Each unanswered question is recorded as an assumption or a deliberate scope boundary.
- The design maps each clarified need to an API, data, control, or explicitly deferred capability.

## 8. Implementation task breakdown and dependencies

| ID | Task | Depends on | Deliverable / done condition |
| --- | --- | --- | --- |
| T1 | Record requirements, assumptions, risks, and scenario boundaries. | None | This document reviewed and committed. |
| T2 | Select stack and define local development, test, and database environment. | T1 | Runnable skeleton, dependency lock/configuration, setup instructions. |
| T3 | Define API contract, error model, data model, indexing, timestamp and pagination semantics. | T1, T2 | Reviewed OpenAPI/schema and architecture decision record. |
| T4 | Specify the integrity model: canonical form, protected fields, hash version/algorithm, genesis value, chain ordering, and concurrency strategy. | T3 | Test vectors and design decision record. |
| T5 | Create migrations and immutable persistence mapping. | T3, T4 | Database schema that supports append, query filters, and chain fields. |
| T6 | Implement transactional append and hash computation. | T4, T5 | Write API plus unit tests, validation tests, and concurrent-append coverage. |
| T7 | Implement filtering and cursor pagination. | T3, T5 | Query API with integration tests for filters and page boundaries. |
| T8 | Implement full-chain verification and violation classification. | T4, T5, T6 | Verification endpoint and direct-tampering integration tests. |
| T9 | Establish quality gates and threat review. | T2 through T8 | Formatting, static analysis, test commands/CI, security findings and resolutions. |
| T10 | Design retention/archival continuity model. | T4, T5, T8 | Architecture decision and tests for archived-chain verification. |
| T11 | Design and implement redaction model and authorization boundary. | T4, T5, T8, T10 | Redaction API/workflow, privacy tests, documented limitations. |
| T12 | Implement verifiable filtered export and independent verifier. | T4, T7, T8 | Bundle format, verifier, and tampering tests. |
| T13 | Clarify and scope compliance-reporting scenario. | T1, T3, T7, T12 | Clarified requirement, design, implementation or justified scope boundary. |
| T14 | Complete final engineering summary and live-defense preparation. | T1 through T13 | Architecture, setup, validation evidence, limitations, AI traceability, and change-ready walkthrough. |

## 9. Dependency sequence

`T1 -> T2/T3 -> T4 -> T5 -> T6 -> T7/T8 -> T9 -> T10 -> T11/T12 -> T13 -> T14`

T7 and T8 may proceed in parallel once append behaviour and the persistence model are stable. Scenario B must not begin until Scenario A's chain semantics are verified, because retention, redaction, and export all depend on a precise integrity model.

## 10. Recommendation for the next commit

Review and personalize this analysis, then commit it as the requirements baseline. The next implementation-focused commit should create only the application/runtime scaffold and local database setup; it should not yet commit to a hash implementation until the canonicalization and concurrency design in T4 has been reviewed.
