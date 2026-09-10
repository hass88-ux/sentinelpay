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

-- Preserve investigations for findings recorded before Part 4 was installed.
INSERT INTO incident_case(id,bucket,currency,state,severity,first_detected_at,evaluated_at)
SELECT CAST(md5(currency || ':' || EXTRACT(EPOCH FROM bucket)::text) AS UUID),bucket,currency,
    CASE WHEN bool_or(state IN ('WARNING','CRITICAL')) THEN 'ALERT'
         WHEN bool_or(first_detected_at IS NOT NULL AND state='INSUFFICIENT_DATA') THEN 'UNDETERMINED'
         ELSE 'CLEARED' END,
    CASE WHEN bool_or(state='CRITICAL') THEN 'CRITICAL'
         WHEN bool_or(state='WARNING') THEN 'WARNING' ELSE 'NONE' END,
    min(first_detected_at),max(evaluated_at)
FROM monitoring_evaluation GROUP BY bucket,currency HAVING bool_or(first_detected_at IS NOT NULL);
