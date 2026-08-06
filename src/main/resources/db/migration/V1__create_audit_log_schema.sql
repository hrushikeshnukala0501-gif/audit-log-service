-- H2 2.x initial schema for the Audit Log Service.

CREATE SEQUENCE audit_event_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE audit_event (
    event_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    chain_sequence BIGINT NOT NULL DEFAULT NEXT VALUE FOR audit_event_sequence UNIQUE,
    event_type VARCHAR(100) NOT NULL CHECK (TRIM(event_type) <> ''),
    actor_id VARCHAR(255) NOT NULL CHECK (TRIM(actor_id) <> ''),
    resource_type VARCHAR(100) NOT NULL CHECK (TRIM(resource_type) <> ''),
    resource_id VARCHAR(255) NOT NULL CHECK (TRIM(resource_id) <> ''),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_commitment VARCHAR(64) NOT NULL,
    payload_ciphertext_hash VARCHAR(64) NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    hash_algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA-256' CHECK (hash_algorithm = 'SHA-256'),
    hash_version SMALLINT NOT NULL DEFAULT 1 CHECK (hash_version > 0)
);

CREATE TABLE audit_event_payload (
    event_id UUID PRIMARY KEY REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    encryption_algorithm VARCHAR(64) NOT NULL,
    encryption_key_reference VARCHAR(512) NOT NULL,
    encryption_nonce BLOB NOT NULL,
    ciphertext BLOB NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE chain_head (
    chain_id SMALLINT PRIMARY KEY CHECK (chain_id = 1),
    head_sequence BIGINT NOT NULL DEFAULT 0 CHECK (head_sequence >= 0),
    head_hash VARCHAR(64) NOT NULL DEFAULT '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39',
    head_event_id UUID NULL REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chain_head_empty_or_populated_check CHECK (
        (head_sequence = 0 AND head_event_id IS NULL
            AND head_hash = '6f631d53cdc1f26efef6d78054e78948c5681f71267c3f9370cf7e5a7a134b39')
        OR (head_sequence > 0 AND head_event_id IS NOT NULL)
    )
);

INSERT INTO chain_head (chain_id, updated_at) VALUES (1, CURRENT_TIMESTAMP);

CREATE TABLE chain_checkpoint (
    checkpoint_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    chain_sequence BIGINT NOT NULL UNIQUE REFERENCES audit_event(chain_sequence) ON DELETE RESTRICT,
    content_hash VARCHAR(64) NOT NULL,
    signing_key_id VARCHAR(255) NOT NULL,
    signature BLOB NOT NULL,
    anchored_at TIMESTAMP WITH TIME ZONE NOT NULL,
    external_reference VARCHAR(1024) NULL
);

CREATE TABLE audit_write_request (
    idempotency_key VARCHAR(128) PRIMARY KEY CHECK (TRIM(idempotency_key) <> ''),
    request_hash VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL UNIQUE REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    accepted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE archive_manifest (
    archive_manifest_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    from_sequence BIGINT NOT NULL CHECK (from_sequence > 0),
    to_sequence BIGINT NOT NULL CHECK (to_sequence >= from_sequence),
    predecessor_hash VARCHAR(64) NOT NULL,
    first_content_hash VARCHAR(64) NOT NULL,
    last_content_hash VARCHAR(64) NOT NULL,
    archive_uri VARCHAR(2048) NOT NULL,
    archive_bundle_hash VARCHAR(64) NOT NULL,
    retention_policy_version VARCHAR(100) NOT NULL,
    archived_by VARCHAR(255) NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (from_sequence, to_sequence)
);

CREATE TABLE payload_redaction (
    redaction_id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    target_event_id UUID NOT NULL REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    redaction_event_id UUID NOT NULL UNIQUE REFERENCES audit_event(event_id) ON DELETE RESTRICT,
    json_pointer VARCHAR(1024) NOT NULL CHECK (json_pointer LIKE '/%'),
    redaction_reason VARCHAR(512) NOT NULL CHECK (TRIM(redaction_reason) <> ''),
    policy_version VARCHAR(100) NOT NULL,
    authorized_by VARCHAR(255) NOT NULL,
    redacted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    key_destruction_reference VARCHAR(512) NOT NULL,
    UNIQUE (target_event_id, json_pointer)
);

CREATE INDEX idx_audit_event_actor_sequence ON audit_event (actor_id, chain_sequence);
CREATE INDEX idx_audit_event_resource_sequence ON audit_event (resource_type, resource_id, chain_sequence);
CREATE INDEX idx_audit_event_type_sequence ON audit_event (event_type, chain_sequence);
CREATE INDEX idx_audit_event_recorded_at_sequence ON audit_event (recorded_at, chain_sequence);
CREATE INDEX idx_audit_event_sequence_hash ON audit_event (chain_sequence, content_hash);
CREATE INDEX idx_payload_redaction_target ON payload_redaction (target_event_id);
CREATE INDEX idx_archive_manifest_range ON archive_manifest (from_sequence, to_sequence);
