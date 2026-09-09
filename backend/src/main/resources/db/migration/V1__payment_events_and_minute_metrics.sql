CREATE TABLE payment_event (
    id varchar(128) PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    amount numeric NOT NULL CHECK (amount > 0),
    currency varchar(3) NOT NULL,
    status varchar(7) NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
    latency_ms bigint NOT NULL CHECK (latency_ms >= 0)
);
CREATE INDEX payment_event_time_idx ON payment_event (occurred_at DESC, id);

CREATE TABLE payment_minute (
    bucket timestamptz NOT NULL,
    currency varchar(3) NOT NULL,
    total_count bigint NOT NULL CHECK (total_count > 0),
    success_count bigint NOT NULL CHECK (success_count >= 0),
    failed_count bigint NOT NULL CHECK (failed_count >= 0),
    total_amount numeric NOT NULL CHECK (total_amount > 0),
    total_latency_ms numeric NOT NULL CHECK (total_latency_ms >= 0),
    max_latency_ms bigint NOT NULL CHECK (max_latency_ms >= 0),
    PRIMARY KEY (bucket, currency),
    CHECK (total_count = success_count + failed_count)
);
