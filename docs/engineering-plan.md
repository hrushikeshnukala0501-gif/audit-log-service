# Engineering Plan

## Delivery sequence

1. **Foundation (current commit):** capture scope, assumptions, execution plan, attestation template, and AI-use traceability.
2. **Application scaffold:** create the Spring Boot project, dependency management, configuration, health endpoint, and local PostgreSQL development setup.
3. **Data model:** add a Flyway migration and immutable audit-event persistence model with indexes for the planned query paths.
4. **Append and integrity:** implement canonical hashing, transactional append sequencing, the write API, and focused unit tests.
5. **Query:** implement validated filtering and stable cursor pagination with integration tests.
6. **Verification:** implement full-chain verification and tests that tamper with the database directly.
7. **Scenario B:** design and implement retention, privacy-preserving redaction, and verifiable export; document trade-offs.
8. **Scenario C and final review:** clarify the compliance-reporting requirement, record scope decisions, complete security/performance review, and prepare the final engineering summary.

## Acceptance criteria for the core service

- A valid event is persisted with server-assigned UTC time, a content hash, and the expected previous hash.
- The service has no update or delete route for audit events.
- Each supported filter can be combined with the others and results paginate without unstable ordering.
- Verification reports an intact chain for normal writes.
- Direct modification of an event's content, stored hash, or predecessor hash causes verification to fail at the earliest inconsistent sequence number with a meaningful violation classification.

## Quality gates for every implementation commit

- Review API validation, error handling, transaction boundaries, and immutability implications.
- Run formatting, static analysis, unit tests, and affected integration tests.
- Add or update tests before declaring behaviour complete.
- Record meaningful AI assistance, edits, and rejected suggestions in `docs/AI_USAGE_LOG.md`.
- Keep a short commit message describing the intent and validation run.

## Risks to address during implementation

- Non-canonical JSON serialization could yield inconsistent hashes across versions or environments.
- Concurrent writers can corrupt chain order without an explicit serialization strategy.
- A hash chain detects tampering but does not prevent a privileged database actor from rewriting an entire chain; anchoring/external checkpoints may be needed for stronger guarantees.
- Payloads can contain sensitive data. Redaction requires a design that preserves evidence without retaining the secret in readable form.
