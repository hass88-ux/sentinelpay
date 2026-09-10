CREATE TABLE monitoring_evaluation (
    bucket TIMESTAMPTZ NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rule VARCHAR(32) NOT NULL CHECK (rule IN ('FAILURE_RATE','AVERAGE_LATENCY','VOLUME_DROP')),
    state VARCHAR(24) NOT NULL CHECK (state IN ('NORMAL','WARNING','CRITICAL','INSUFFICIENT_DATA')),
    sample_count BIGINT NOT NULL CHECK (sample_count > 0),
    observed NUMERIC NOT NULL,
    warning_threshold NUMERIC,
    critical_threshold NUMERIC,
    explanation TEXT NOT NULL,
    first_detected_at TIMESTAMPTZ,
    evaluated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bucket,currency,rule)
);
CREATE INDEX monitoring_findings_recent ON monitoring_evaluation(bucket DESC)
    WHERE first_detected_at IS NOT NULL;
