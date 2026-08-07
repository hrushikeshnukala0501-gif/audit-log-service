# Tamper-Evident Audit Log Service

A production-oriented prototype of an append-only audit log service. The service will accept audit events, support filtered and paginated queries, and expose verification of a cryptographic hash chain so data-store tampering is detectable.

## Status

Scenario A is implemented: append an audit event, query audit events, and verify the complete tamper-evident hash chain. Manual API validation and automated test coverage remain pending.

## Planned technology choices

- Java 21 and Spring Boot 3
- H2 2.x for local, embedded prototype storage
- SHA-256 hash chain using a canonical event representation
- Flyway for database migrations
- JUnit 5 and Spring Boot integration tests (to be added)

H2 is intentionally local-development storage. A production deployment needs a durable managed relational database and externally managed encryption keys.

## Repository guide

- `docs/requirements-and-scope.md` - normalized requirements, assumptions, and boundaries
- `docs/engineering-plan.md` - incremental delivery plan and quality gates
- `docs/AI_USAGE_LOG.md` - traceability log for AI-assisted work
- `ATTESTATION.md` - required individual-work attestation; personalize before submission

## Run locally

The application uses an in-memory H2 database by default, so no external database is required. Before starting it, set a Base64-encoded 32-byte AES key in your IDE run configuration or shell. Do not commit this key.

PowerShell example for a local development key:

```powershell
[Convert]::ToBase64String([byte[]](0..31))
```

Set the output as the `AUDIT_PAYLOAD_ENCRYPTION_KEY` environment variable, then run:

```powershell
mvn spring-boot:run
```

Once started:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 Console: `http://localhost:8080/h2-console`
- Health: `http://localhost:8080/actuator/health`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs/audit-log-service`

Use Swagger UI's **Try it out** button. Enter only the endpoint URL/path in the request URL field; add `Content-Type: application/json` in the request header section and the JSON request in the body section.

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
- Maven 3.9+

## Validation

Run `mvn clean test` after stopping processes that lock the project-local Maven cache. Full testing and manual Swagger procedures, including H2 tamper validation and Scenario A/B/C sequences, are in [docs/Testing.md](docs/Testing.md).

The repository's `.mvn/maven.config` forces Maven to refresh cached dependency failures and uses the ignored project-local `.m2/repository` cache. This avoids IDE reload failures caused by an inaccessible global Maven repository.
