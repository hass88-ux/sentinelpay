-- Separate from the simulator's shared pipeline and Supabase's public API schema.
CREATE SCHEMA IF NOT EXISTS sentinelpay_private;
CREATE TABLE sentinelpay_private.upload (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    filename varchar(128) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    transaction_count integer NOT NULL CHECK (transaction_count BETWEEN 1 AND 5000),
    UNIQUE (id, owner_id)
);
CREATE INDEX upload_owner_created ON sentinelpay_private.upload (owner_id, created_at DESC);
CREATE TABLE sentinelpay_private.upload_event (
    upload_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    id varchar(128) NOT NULL,
    occurred_at timestamptz NOT NULL,
    amount numeric NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL,
    status varchar(7) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    latency_ms bigint NOT NULL CHECK (latency_ms >= 0),
    PRIMARY KEY (upload_id, id),
    FOREIGN KEY (upload_id, owner_id) REFERENCES sentinelpay_private.upload (id, owner_id) ON DELETE CASCADE
);
CREATE INDEX upload_event_owner ON sentinelpay_private.upload_event (owner_id, upload_id, occurred_at);
-- Browser roles receive no direct access. Java accesses this schema through its database role.
REVOKE ALL ON SCHEMA sentinelpay_private FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA sentinelpay_private FROM PUBLIC;
