CREATE TABLE messages
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    trip_id    UUID        NOT NULL,
    sender_id  VARCHAR(36) NOT NULL,
    content    TEXT        NOT NULL,
    sent_at    TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);
CREATE INDEX idx_messages_trip_id ON messages (trip_id);
CREATE INDEX idx_messages_sent_at ON messages (sent_at DESC);
