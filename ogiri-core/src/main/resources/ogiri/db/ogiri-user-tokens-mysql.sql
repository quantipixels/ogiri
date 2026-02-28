-- MySQL/MariaDB schema for ogiri's Token JPA entity.
-- Adjust the foreign key target to your user table name if it differs.
CREATE
    TABLE
        IF NOT EXISTS user_tokens(
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            client VARCHAR(255) NOT NULL,
            token_hash VARCHAR(255) NOT NULL,
            token_type VARCHAR(20) NOT NULL,
            token_subtype VARCHAR(64),
            expiry_at TIMESTAMP(6) NOT NULL,
            previous_token_hash VARCHAR(255),
            last_token_hash VARCHAR(255),
            token_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_used_at TIMESTAMP(6),
            created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP ON
            UPDATE
                CURRENT_TIMESTAMP,
                UNIQUE KEY uk_user_tokens_user_client(
                    user_id,
                    client
                ),
                INDEX idx_user_tokens_user_id(user_id),
                INDEX idx_user_tokens_expiry(expiry_at),
                INDEX idx_user_tokens_user_subtype(
                    user_id,
                    token_subtype
                )
        );

-- Optional: uncomment and point to your users table when ready.
-- ALTER TABLE user_tokens ADD CONSTRAINT fk_user_tokens_user_id FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE;
