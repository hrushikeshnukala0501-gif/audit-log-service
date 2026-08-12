# Tamper-Evident Audit Log Service

A production-oriented append-only audit log prototype. It records protected audit events, supports filtering and keyset pagination, and verifies a SHA-256 hash chain to detect persisted-data tampering.

## Status

Scenario A (append/query/verify), Scenario B (logical archive manifests, response-projection redaction, and export), and Scenario C (account access reporting) are implemented. API-key authentication protects operational endpoints. The automated suite currently contains 46 passing tests; see [docs/Testing.md](docs/Testing.md) for scope and limitations.

## Technology

- Java 21 and Spring Boot 3
- H2 2.x for local, embedded prototype storage; optional PostgreSQL Docker profile
- SHA-256 hash chain using a canonical event representation
- Flyway for database migrations
- JUnit 5, MockMvc, ArchUnit, and Spring Boot integration tests

H2 is intentionally local-development storage. A production deployment needs a durable managed relational database and externally managed encryption keys.

## Repository guide

- `docs/requirements-and-scope.md` - normalized requirements, assumptions, and boundaries
- `docs/engineering-plan.md` - incremental delivery plan and quality gates
- `docs/AI_USAGE_LOG.md` - traceability log for AI-assisted work
- `ATTESTATION.md` - individual-work attestation

## Run locally

The application uses an in-memory H2 database by default, so no external database is required. To use Swagger UI and the H2 Console locally, start with the `dev` profile. Do not commit either secret.

PowerShell example for a local development key:

```powershell
[Convert]::ToBase64String([byte[]](0..31))
```

Set the output as `AUDIT_PAYLOAD_ENCRYPTION_KEY`, set an API key, enable the dev profile, then run:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
$env:AUDIT_PAYLOAD_ENCRYPTION_KEY='<base64-encoded-32-byte-key>'
$env:AUDIT_API_KEY='local-development-api-key'
.\mvnw.cmd spring-boot:run
```

Once started:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`
- Health: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs/audit-log-service`

Use Swagger UI's **Authorize** button to set `X-API-Key` to the value of `AUDIT_API_KEY`. Every `/api/v1/**` call requires that header. The H2 Console and Swagger/OpenAPI are disabled outside the `dev` profile.

The H2 console connection details are the default JDBC URL in `src/main/resources/application.yml`, user `sa`, and a blank password unless overridden.

## Scenario A API routes

- `POST /api/v1/audit/events` — append an immutable audit event.
- `GET /api/v1/audit/events` — filter and cursor-page audit events.
- `GET /api/v1/audit/verify` — verify the stored hash chain.
- `POST /api/v1/audit/retention/archive` — create the next eligible logical archive manifest.
- `POST /api/v1/audit/events/{eventId}/redactions` — redact one JSON Pointer path from response projections.
- `GET /api/v1/audit/export?actorId=...` or `?resourceId=...` — produce a verifiable export bundle.
- `GET /api/v1/compliance/client-account-access?accountId=...` — report access to one client account without plaintext payloads.

## Build prerequisites

- Java 21
- Maven is supplied by the committed Maven Wrapper

## Validation

Run `./mvnw verify` on macOS/Linux or `.\mvnw.cmd clean verify` on Windows. Full testing and manual Swagger procedures, including H2 tamper validation and Scenario A/B/C sequences, are in [docs/Testing.md](docs/Testing.md).
