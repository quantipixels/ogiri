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
 * Sample JPA Token entity extending OgiriBaseTokenEntity.
 *
 * All 15+ token fields with proper JPA annotations are inherited from OgiriBaseTokenEntity. This
 * class only needs to add @Entity, @Table annotations, and any custom indexes/constraints.
 */
@Entity
@Table(
    name = "user_tokens",
    indexes =
        [
            Index(name = "idx_user_tokens_user_id", columnList = "user_id"),
            Index(name = "idx_user_tokens_expiry", columnList = "expiry_at"),
            Index(name = "idx_user_tokens_prefix", columnList = "token_prefix"),
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
