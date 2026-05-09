-- Phase 1: introduce trip_member_id alongside sender_id (device UUID) for cross-service identity migration.
-- See docs/PLAN_device_id_to_member_id.md.

ALTER TABLE messages
    ADD COLUMN sender_member_id UUID;

CREATE INDEX idx_messages_sender_member_id ON messages (sender_member_id);
