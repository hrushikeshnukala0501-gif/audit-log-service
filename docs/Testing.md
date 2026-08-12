# Testing and Production-Readiness Review

## Objective and environment

Validate Scenario A append/query/verification, Scenario B retention/redaction/export, and Scenario C client-account access reporting using Java 21, Spring Boot, Flyway, and the embedded H2 database. Use fictional data only. H2 is reset by restarting the application because the configured database is in memory.

## Current automated-test status

The repository contains focused unit tests, H2/Spring Boot integration tests, direct-datastore tamper-detection tests, architecture tests, and a concurrent-append test. The last successful H2 verification before adding the environment work ran 40 tests with 0 failures, 0 errors, and 0 skipped. The test suite uses the embedded H2/Flyway configuration; PostgreSQL migration and runtime wiring are provided for Docker Compose, but PostgreSQL integration tests are not yet part of CI.

## Commands

```text
./mvnw verify                 # macOS/Linux
mvnw.cmd verify               # Windows
./mvnw spring-boot:run        # macOS/Linux
mvnw.cmd spring-boot:run      # Windows
```

The Maven Wrapper downloads the project-pinned Maven version when needed. Java 21 must be available on `PATH`.

To run the production-like PostgreSQL container stack, set non-committed local values for `POSTGRES_PASSWORD` and `AUDIT_PAYLOAD_ENCRYPTION_KEY`, then run:

```text
docker compose up --build
```

The Compose application uses the `postgres` Spring profile and applies the PostgreSQL Flyway migration. Stop it with `docker compose down`; add `-v` only when intentionally deleting the local PostgreSQL volume.

## Authentication and development tools

All `/api/v1/**` calls require an `X-API-Key` header containing the value of
`AUDIT_API_KEY`. Missing or invalid keys return the standard `401` error
envelope. Swagger UI and the H2 Console are disabled by default; start with the
`dev` profile to enable them locally, then use Swagger's **Authorize** action to
set the API key once for all requests.

```text
SPRING_PROFILES_ACTIVE=dev
AUDIT_API_KEY=local-development-api-key
```

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs/audit-log-service`

## Observed validation result

On 2026-08-12, `mvnw.cmd clean verify` completed successfully under Java 21.0.12. Flyway validated and applied the H2 migration, Hibernate initialized, and the suite executed 46 tests: 46 passed, 0 failed, 0 errors, and 0 skipped. The run includes hash-chain tamper cases, risk-bearing unit tests, architecture checks, Scenario A-C integration flows, concurrent append validation, bounded verification, API-key authentication, and default/dev tool-exposure tests. The suite also previously exposed and corrected timestamp precision in hash inputs: server timestamps are normalized to microseconds before hashing and persistence.

## Manual Scenario A procedure

1. Start with `SPRING_PROFILES_ACTIVE=dev` and set `AUDIT_API_KEY`; include that value as `X-API-Key` on every API call.
2. POST three fictional events to `/api/v1/audit/events`.
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
- A single shared API key protects operational APIs. Per-user identity, authorization roles, expiry, key rotation, and secret-manager integration are not implemented.
- Redaction is response projection, not per-field cryptographic erasure.
- Non-contiguous exports/reports do not prove omitted global-chain links without signed checkpoints or intervening records.
- JaCoCo report generation and a GitHub Actions `verify` workflow are configured. No minimum coverage threshold, static analysis, vulnerability scan, or load test is configured.
- Swagger/OpenAPI was initialized by SpringDoc during the integration-test application context, but browser rendering of Swagger UI remains a manual check.
