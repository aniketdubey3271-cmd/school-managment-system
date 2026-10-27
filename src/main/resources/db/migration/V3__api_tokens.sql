-- Mobile app sessions: one row per signed-in device. Only SHA-256 hashes of the tokens are stored.
CREATE TABLE api_tokens (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id             INTEGER     NOT NULL,
    access_hash         VARCHAR(64) NOT NULL UNIQUE,
    access_expires_at   TIMESTAMP   NOT NULL,
    refresh_hash        VARCHAR(64) NOT NULL UNIQUE,
    refresh_expires_at  TIMESTAMP   NOT NULL,
    created_at          TIMESTAMP   NOT NULL,
    CONSTRAINT fk_api_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_api_tokens_user ON api_tokens (user_id);
