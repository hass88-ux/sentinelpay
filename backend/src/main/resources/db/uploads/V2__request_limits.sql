CREATE TABLE sentinelpay_private.request_limit (
    owner_id uuid NOT NULL,
    operation varchar(24) NOT NULL,
    minute_start timestamptz NOT NULL,
    minute_count integer NOT NULL,
    day_start timestamptz NOT NULL,
    day_count integer NOT NULL,
    PRIMARY KEY (owner_id, operation)
);
REVOKE ALL ON sentinelpay_private.request_limit FROM PUBLIC;
