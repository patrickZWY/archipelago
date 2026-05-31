CREATE TABLE friendships (
    id BIGSERIAL PRIMARY KEY,
    requester_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    creation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_friendships_distinct_users CHECK (requester_user_id <> recipient_user_id)
);

CREATE UNIQUE INDEX uk_friendships_directional_pair
    ON friendships (requester_user_id, recipient_user_id);

CREATE INDEX idx_friendships_requester_status
    ON friendships (requester_user_id, status);

CREATE INDEX idx_friendships_recipient_status
    ON friendships (recipient_user_id, status);
