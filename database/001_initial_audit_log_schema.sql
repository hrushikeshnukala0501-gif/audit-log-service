-- PostgreSQL 16+ reference schema for the Audit Log Service.
-- This is a schema design artifact. Apply it through a migration tool (for example,
-- Flyway) after the application scaffold and database environment are in place.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- One immutable row per accepted audit event. The application runtime role must have
-- INSERT and SELECT only; do not grant UPDATE or DELETE on this table.
CREATE TABLE audit_event (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE NOT NULL,
    event_type VARCHAR(100) NOT NULL CHECK (btrim(event_type) <> ''),
    actor_id VARCHAR(255) NOT NULL CHECK (btrim(actor_id) <> ''),
    resource_type VARCHAR(100) NOT NULL CHECK (btrim(resource_type) <> ''),
    resource_id VARCHAR(255) NOT NULL CHECK (btrim(resource_id) <> ''),
    recorded_at TIMESTAMPTZ NOT NULL,
    payload_commitment CHAR(64) NOT NULL CHECK (payload_commitment ~ '^[0-9a-f]{64}$'),
    payload_ciphertext_hash CHAR(64) NOT NULL CHECK (payload_ciphertext_hash ~ '^[0-9a-f]{64}$'),
    previous_hash CHAR(64) NOT NULL CHECK (previous_hash ~ '^[0-9a-f]{64}$'),
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    hash_algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA-256' CHECK (hash_algorithm = 'SHA-256'),
    hash_version SMALLINT NOT NULL DEFAULT 1 CHECK (hash_version > 0),
    CONSTRAINT audit_event_first_link_check CHECK (
        (chain_sequence = 1 AND previous_hash = '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39')
        OR (chain_sequence > 1 AND previous_hash <> '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39')
    )
);

-- The encrypted, retrievable payload is separate from the immutable event metadata.
-- payload_commitment is SHA-256 of canonical plaintext JSON; payload_ciphertext_hash is
-- SHA-256 of ciphertext. Verification can detect ciphertext alteration and, when the
-- key is available, confirm that decrypted plaintext matches the commitment.
CREATE TABLE audit_event_payload (
    event_id UUID PRIMARY KEY REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    encryption_algorithm VARCHAR(64) NOT NULL,
    encryption_key_reference VARCHAR(512) NOT NULL,
    encryption_nonce BYTEA NOT NULL CHECK (octet_length(encryption_nonce) > 0),
    ciphertext BYTEA NOT NULL CHECK (octet_length(ciphertext) > 0),
    stored_at TIMESTAMPTZ NOT NULL
);

-- One row coordinates serial append of the global chain. It is mutable coordination
-- state, not audit history; append transactions lock it with SELECT ... FOR UPDATE.
CREATE TABLE chain_head (
    chain_id SMALLINT PRIMARY KEY CHECK (chain_id = 1),
    head_sequence BIGINT NOT NULL DEFAULT 0 CHECK (head_sequence >= 0),
    head_hash CHAR(64) NOT NULL DEFAULT '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39'
        CHECK (head_hash ~ '^[0-9a-f]{64}$'),
    head_event_id UUID NULL REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chain_head_empty_or_populated_check CHECK (
        (head_sequence = 0 AND head_event_id IS NULL
            AND head_hash = '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39')
        OR (head_sequence > 0 AND head_event_id IS NOT NULL)
    )
);

INSERT INTO chain_head (chain_id, updated_at) VALUES (1, CURRENT_TIMESTAMP);

-- Optional externally signed anchors for stronger evidence and faster prefix checks.
CREATE TABLE chain_checkpoint (
    checkpoint_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chain_sequence BIGINT NOT NULL UNIQUE REFERENCES audit_event(chain_sequence) ON DELETE RESTRICT,
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    signing_key_id VARCHAR(255) NOT NULL,
    signature BYTEA NOT NULL CHECK (octet_length(signature) > 0),
    anchored_at TIMESTAMPTZ NOT NULL,
    external_reference VARCHAR(1024) NULL
);

-- Records idempotent append attempts without changing the immutable event.
CREATE TABLE audit_write_request (
    idempotency_key VARCHAR(128) PRIMARY KEY CHECK (btrim(idempotency_key) <> ''),
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    event_id UUID NOT NULL UNIQUE REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    accepted_at TIMESTAMPTZ NOT NULL
);

-- Future retention support. The manifest describes a contiguous archived range and
-- its cryptographic boundaries. It intentionally has no FK to event rows because
-- archived event rows may later move to external immutable storage.
CREATE TABLE archive_manifest (
    archive_manifest_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_sequence BIGINT NOT NULL CHECK (from_sequence > 0),
    to_sequence BIGINT NOT NULL CHECK (to_sequence >= from_sequence),
    predecessor_hash CHAR(64) NOT NULL CHECK (predecessor_hash ~ '^[0-9a-f]{64}$'),
    first_content_hash CHAR(64) NOT NULL CHECK (first_content_hash ~ '^[0-9a-f]{64}$'),
    last_content_hash CHAR(64) NOT NULL CHECK (last_content_hash ~ '^[0-9a-f]{64}$'),
    archive_uri VARCHAR(2048) NOT NULL,
    archive_bundle_hash CHAR(64) NOT NULL CHECK (archive_bundle_hash ~ '^[0-9a-f]{64}$'),
    retention_policy_version VARCHAR(100) NOT NULL,
    archived_by VARCHAR(255) NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL,
    UNIQUE (from_sequence, to_sequence)
);

-- Future redaction support. The redaction itself is recorded as a new audit event;
-- the target event remains unchanged. Destruction/revocation of the referenced key
-- makes the selected plaintext inaccessible while payload_commitment preserves proof.
CREATE TABLE payload_redaction (
    redaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_event_id UUID NOT NULL REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    redaction_event_id UUID NOT NULL UNIQUE REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    json_pointer VARCHAR(1024) NOT NULL CHECK (json_pointer LIKE '/%'),
    redaction_reason VARCHAR(512) NOT NULL CHECK (btrim(redaction_reason) <> ''),
    policy_version VARCHAR(100) NOT NULL,
    authorized_by VARCHAR(255) NOT NULL,
    redacted_at TIMESTAMPTZ NOT NULL,
    key_destruction_reference VARCHAR(512) NOT NULL,
    UNIQUE (target_event_id, json_pointer)
);

-- Query indexes. Each ends in chain_sequence to support a stable seek cursor.
CREATE INDEX idx_audit_event_actor_sequence
    ON audit_event (actor_id, chain_sequence);

CREATE INDEX idx_audit_event_resource_sequence
    ON audit_event (resource_type, resource_id, chain_sequence);

CREATE INDEX idx_audit_event_type_sequence
    ON audit_event (event_type, chain_sequence);

CREATE INDEX idx_audit_event_recorded_at_sequence
    ON audit_event (recorded_at, chain_sequence);

CREATE INDEX idx_audit_event_sequence_hash
    ON audit_event (chain_sequence, content_hash);

CREATE INDEX idx_payload_redaction_target
    ON payload_redaction (target_event_id);

CREATE INDEX idx_archive_manifest_range
    ON archive_manifest (from_sequence, to_sequence);

COMMIT;
