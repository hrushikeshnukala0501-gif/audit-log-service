# Testing and Production-Readiness Review

## Objective and environment

Validate Scenario A append/query/verification, Scenario B retention/redaction/export, and Scenario C client-account access reporting using Java 21, Spring Boot, Flyway, and the embedded H2 database. Use fictional data only. H2 is reset by restarting the application because the configured database is in memory.

## Current automated-test status

There are currently no `src/test` sources. Automated integration coverage is therefore **not yet implemented**. The planned approach is `@SpringBootTest` with `@AutoConfigureMockMvc` and the existing embedded H2/Flyway setup; no additional test stack is required.

The first required automated suite should cover: genesis and predecessor links; validation/malformed JSON; combined filters/cursor paging; direct H2 tampering followed by verification; retention replay/idempotency; top-level and nested redactions; actor/resource export selector validation; and compliance account filtering. A concurrent-write test should assert distinct sequences and predecessor links, subject to H2 transaction scheduling reliability.

## Commands

```powershell
$env:JAVA_HOME='C:\Users\Saikr\.jdks\ms-21.0.12'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn clean test
mvn spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs/audit-log-service`

## Observed validation result

On 2026-08-06, `mvn test` attempted to compile 60 source files under Java 21 but failed before test discovery: `Fatal Error: Cannot close compiler resources` for the project-local `.m2/repository/.../spring-beans-6.2.19.jar`. This is a Windows file lock, not a reported source compilation failure. Stop IntelliJ/application processes that use the project-local Maven cache, then rerun the commands above. Do not report test counts until this passes.

## Manual Scenario A procedure

1. POST three fictional events to `/api/v1/audit/events`.
2. Query unfiltered and with actor, resource type/ID, event type, time, combined filters, cursors, and both sort directions.
3. Call `/api/v1/audit/verify` and confirm `intact=true`.
4. In H2 Console (`/h2-console`), run `UPDATE audit_event SET actor_id='tampered' WHERE chain_sequence=1`.
5. Verify again; expect `intact=false` and the first violation without payload data.

## Manual Scenario B procedure

1. Set `AUDIT_RETENTION_ARCHIVE_AFTER=PT0S`, restart, and POST `/api/v1/audit/retention/archive` twice; expect `201`, then `204`.
2. Create an event containing a fictional sensitive field, POST a redaction to `/api/v1/audit/events/{eventId}/redactions`, query it, and confirm `[REDACTED]`.
3. Verify the chain remains intact after redaction.
4. Call `/api/v1/audit/export` once with `actorId`, once with `resourceId`, and with zero/both selectors; expect `200`, `200`, and `400` respectively.

## Manual Scenario C procedure

1. Create `CLIENT_ACCOUNT_ACCESSED` events for one `ACCOUNT` resource and unrelated events/accounts.
2. Call `/api/v1/compliance/client-account-access?accountId=...` with and without an inclusive UTC range.
3. Confirm only the requested account appears, plaintext payload is absent, and report hash/boundary metadata is present.

## Limitations and production risks

- H2 and in-memory data are local-prototype only; use a durable managed database in production.
- No authentication/RBAC protects write, retention, redaction, export, or compliance endpoints.
- Redaction is response projection, not per-field cryptographic erasure.
- Non-contiguous exports/reports do not prove omitted global-chain links without signed checkpoints or intervening records.
- No automated tests, coverage measurement, static analysis, vulnerability scan, or load test is currently configured.
- Swagger/OpenAPI runtime loading remains unverified until the Maven file lock is resolved.
