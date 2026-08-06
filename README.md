# Tamper-Evident Audit Log Service

A production-oriented prototype of an append-only audit log service. The service will accept audit events, support filtered and paginated queries, and expose verification of a cryptographic hash chain so data-store tampering is detectable.

## Status

Project foundation and requirements analysis are complete. The application scaffold, API, persistence, and tests will be added in small, reviewable commits.

## Planned technology choices

- Java 21 and Spring Boot 3
- PostgreSQL for durable event storage
- SHA-256 hash chain using a canonical event representation
- Flyway for database migrations
- JUnit 5, Testcontainers, and REST integration tests

These choices are documented as provisional until the architecture commit, where the data model and hash canonicalization rules will be finalized.

## Repository guide

- `docs/requirements-and-scope.md` - normalized requirements, assumptions, and boundaries
- `docs/engineering-plan.md` - incremental delivery plan and quality gates
- `docs/AI_USAGE_LOG.md` - traceability log for AI-assisted work
- `ATTESTATION.md` - required individual-work attestation; personalize before submission

## Intended local run instructions

The exact commands will be added with the application scaffold. The target workflow is a local Java runtime plus PostgreSQL (Docker Compose) and a single command to run the service and test suite.
