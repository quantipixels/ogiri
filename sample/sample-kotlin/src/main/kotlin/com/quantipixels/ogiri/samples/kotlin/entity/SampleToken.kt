/*
 * Copyright (c) 2025 Quanti Pixels
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.quantipixels.ogiri.samples.kotlin.entity

import com.quantipixels.ogiri.jpa.OgiriBaseTokenEntity
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Sample JPA Token implementation using ogiri-jpa module.
 *
 * This demonstrates the simplified approach using OgiriBaseTokenEntity from ogiri-jpa. All token
 * fields (id, userId, client, token, expiryAt, etc.) are inherited from OgiriBaseTokenEntity with
 * proper JPA annotations already configured.
 *
 * Users only need to:
 * 1. Add @Entity annotation
 * 2. Add @Table with custom table name and indexes
 * 3. Optionally add custom fields if needed
 *
 * This reduces boilerplate from ~80 lines to just ~10 lines while maintaining full JPA
 * functionality.
 *
 * Before (manual implementation): ~80 lines with all field declarations and annotations After
 * (using OgiriBaseTokenEntity): ~10 lines with just @Entity and @Table
 */
@Entity
@Table(
    name = "user_tokens",
    indexes =
        [
            Index(name = "idx_user_tokens_user_id", columnList = "user_id"),
            Index(name = "idx_user_tokens_expiry", columnList = "expiry_at"),
        ],
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_user_tokens_user_client",
                columnNames = ["user_id", "client"],
            ),
        ],
)
class SampleToken : OgiriBaseTokenEntity()
