CREATE TABLE incident_case (
    id UUID PRIMARY KEY,
    bucket TIMESTAMPTZ NOT NULL,
    currency VARCHAR(3) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK (state IN ('ALERT','CLEARED','UNDETERMINED')),
    severity VARCHAR(8) NOT NULL CHECK (severity IN ('CRITICAL','WARNING','NONE')),
    first_detected_at TIMESTAMPTZ NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(bucket,currency)
);
CREATE INDEX incident_case_recent ON incident_case(bucket DESC,id);
