ALTER TABLE users ADD COLUMN account_status VARCHAR(50);
ALTER TABLE users ADD COLUMN verification_token_hash VARCHAR(128);
ALTER TABLE users ADD COLUMN verification_token_expire_time TIMESTAMP;
ALTER TABLE users ADD COLUMN password_reset_token_hash VARCHAR(128);
ALTER TABLE users ADD COLUMN session_revoked_before TIMESTAMP;

UPDATE users
SET account_status = CASE
    WHEN deleted = TRUE THEN 'DELETED'
    WHEN enabled = FALSE THEN 'DISABLED'
    WHEN verified = FALSE THEN 'PENDING_VERIFICATION'
    ELSE 'ACTIVE'
END;

ALTER TABLE users ALTER COLUMN account_status SET NOT NULL;

CREATE INDEX idx_users_account_status ON users (account_status);
CREATE INDEX idx_users_verification_token_hash ON users (verification_token_hash);
CREATE INDEX idx_users_password_reset_token_hash ON users (password_reset_token_hash);

ALTER TABLE users DROP COLUMN verification_token;
ALTER TABLE users DROP COLUMN password_reset_token;

CREATE TABLE auth_audit_events (
    id BIGSERIAL PRIMARY KEY,
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    ip_hash VARCHAR(128) NOT NULL,
    user_agent_hash VARCHAR(128) NOT NULL
);

CREATE INDEX idx_auth_audit_events_event_time ON auth_audit_events (event_time);
CREATE INDEX idx_auth_audit_events_user_id ON auth_audit_events (user_id);
CREATE INDEX idx_auth_audit_events_event_type ON auth_audit_events (event_type);
