CREATE TABLE login_otps (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_token VARCHAR(64) NOT NULL UNIQUE,
    code_hash VARCHAR(255) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_login_otps_challenge_token ON login_otps(challenge_token);
CREATE INDEX idx_login_otps_user_id ON login_otps(user_id);
