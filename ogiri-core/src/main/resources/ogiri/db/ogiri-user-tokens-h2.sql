-- H2 schema for ogiri's Token JPA entity (in-memory database for testing).
-- Adjust the foreign key target to your user table name if it differs.
CREATE
    TABLE
        IF NOT EXISTS user_tokens(
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            client_id VARCHAR(255) NOT NULL,
            token_hash VARCHAR(255) NOT NULL,
            token_type VARCHAR(20) NOT NULL,
            token_subtype VARCHAR(64),
            token_prefix VARCHAR(8),
            expiry_at TIMESTAMP(6) NOT NULL,
            previous_token_hash VARCHAR(255),
            last_token_hash VARCHAR(255),
            token_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_used_at TIMESTAMP(6),
            created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(
                user_id,
                client_id
            )
        );

CREATE
    INDEX IF NOT EXISTS idx_user_tokens_user_id ON
    user_tokens(user_id);

CREATE
    INDEX IF NOT EXISTS idx_user_tokens_expiry ON
    user_tokens(expiry_at);

CREATE
    INDEX IF NOT EXISTS idx_user_tokens_prefix ON
    user_tokens(token_prefix);

-- Optional: uncomment and point to your users table when ready.
-- ALTER TABLE user_tokens ADD CONSTRAINT fk_user_tokens_user_id FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE;
