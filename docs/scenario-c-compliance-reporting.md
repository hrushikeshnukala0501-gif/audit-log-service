# Scenario C - Compliance Reporting Clarification and Scope

## Business statement

Regulators need to be able to audit access to client account data.

## Ambiguities and clarification questions

1. Which account resources are in scope: brokerage accounts only, all client financial products, or linked profiles?
2. Which actions count as access: read, search, export, download, failed attempts, automated processing, or administrator access?
3. Who are the actors: employees, advisers, service accounts, vendors, and clients?
4. Which regulator receives the report, in which format, and at what cadence?
5. What date range, retention period, legal hold, and timezone rules apply?
6. Which fields may be disclosed to the regulator, and how are redacted values represented?
7. Who may generate, view, and export a report, and must each report generation itself be audited?
8. Is a report required to prove a complete account access history, or only to list matching events?

## Prototype assumptions

- A client account is represented by `resourceType=ACCOUNT` and its account identifier is `resourceId`.
- Account-access events use the event type `CLIENT_ACCOUNT_ACCESSED` (or a documented future access-event taxonomy).
- `actorId` identifies the human or workload principal responsible for the access.
- UTC time boundaries are inclusive and report values contain no decrypted sensitive payload content.
- The existing caller is trusted for the prototype. Authentication, authorisation, regulator delivery, and legal-hold enforcement are outside the selected scope.

## Clarified technical requirement

The service shall provide a read-only endpoint that returns all immutable audit events for one client account, optionally constrained by an inclusive UTC time range. The report shall include actor, access event type, server-recorded time, immutable chain sequence, and cryptographic integrity metadata. It shall exclude plaintext payloads and allow an independent reviewer to correlate returned records to the global hash chain.

## Selected implementation

`GET /api/v1/compliance/client-account-access` accepts `accountId`, optional `from`, and optional `to`. It reads only `ACCOUNT` events with the supplied resource ID, validates the time range, returns records in immutable sequence order, and includes genesis, first/last boundaries, predecessor/content hashes, and a canonical report digest. No write, update, or delete operation is exposed.

## Scope boundaries and trade-offs

- The report is an account-focused filtered view, not evidence of every global-chain intermediate record. Boundary hashes establish each record's declared chain position; a signed checkpoint or full intervening segment is required for a complete global proof.
- Payloads are omitted to reduce privacy exposure. Redaction state is represented by the existing query/redaction mechanism rather than duplicating plaintext in reports.
- The service does not claim regulatory compliance. Jurisdictional retention, access-control, approval, signing, delivery, and legal-hold requirements require stakeholder confirmation.

## Future enhancements

- RBAC and a separate regulator-reporting permission.
- Report-generation audit events, signed exports, trusted checkpoints, and immutable delivery receipts.
- Configurable access-event taxonomy, report templates, legal holds, and regulator-specific retention policies.
- Asynchronous large-report generation with object storage and expiring download links.
