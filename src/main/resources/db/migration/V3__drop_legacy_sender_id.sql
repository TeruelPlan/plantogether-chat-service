-- Phase 3: drop legacy sender_id column now that all callers consume sender_member_id.
-- See docs/PROGRESS_device_id_to_member_id.md.

ALTER TABLE messages
    DROP COLUMN sender_id;

ALTER TABLE messages
    ALTER COLUMN sender_member_id SET NOT NULL;
