-- Deploy the API that sets transaction-local owner context BEFORE applying this migration.
-- Missing or empty context matches no rows. The runtime role must not own these tables
-- or have SUPERUSER/BYPASSRLS privileges. Existing Java owner predicates remain in place.
ALTER TABLE sentinelpay_private.upload ENABLE ROW LEVEL SECURITY;
ALTER TABLE sentinelpay_private.upload FORCE ROW LEVEL SECURITY;
ALTER TABLE sentinelpay_private.upload_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE sentinelpay_private.upload_event FORCE ROW LEVEL SECURITY;

CREATE POLICY upload_owner ON sentinelpay_private.upload
    USING (owner_id = nullif(current_setting('sentinelpay.owner_id', true), '')::uuid)
    WITH CHECK (owner_id = nullif(current_setting('sentinelpay.owner_id', true), '')::uuid);
CREATE POLICY upload_event_owner ON sentinelpay_private.upload_event
    USING (owner_id = nullif(current_setting('sentinelpay.owner_id', true), '')::uuid)
    WITH CHECK (owner_id = nullif(current_setting('sentinelpay.owner_id', true), '')::uuid);
