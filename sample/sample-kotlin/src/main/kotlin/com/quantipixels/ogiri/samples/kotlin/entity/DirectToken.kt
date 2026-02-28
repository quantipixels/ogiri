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

import com.quantipixels.ogiri.security.tokens.OgiriToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

/**
 * Example token implementation using direct interface implementation.
 *
 * This demonstrates how to implement OgiriToken directly without extending OgiriBaseToken. This
 * approach provides maximum flexibility - you can:
 * - Implement your own base class and have both extend OgiriToken
 * - Add custom fields and methods
 * - Control inheritance hierarchy completely
 *
 * Compare this to [SampleToken] which extends OgiriBaseToken for convenience.
 */
@Entity
@Table(
    name = "direct_tokens",
    indexes =
        [
            Index(name = "idx_direct_tokens_user_id", columnList = "user_id"),
            Index(name = "idx_direct_tokens_expiry", columnList = "expiry_at"),
        ],
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_direct_tokens_user_client",
                columnNames = ["user_id", "client"],
            ),
        ],
)
data class DirectToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override val id: Long = 0,
    @Column(name = "user_id", nullable = false) override val userId: Long = 0,
    @Column(name = "client", nullable = false) override val client: String = "",
    @Column(name = "token_hash", nullable = false) override var token: String = "",
    @Column(name = "token_type", nullable = false) override val tokenType: String = "app",
    @Column(name = "expiry_at", nullable = false) override var expiryAt: Instant = Instant.now(),
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    override val createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    override val updatedAt: Instant = Instant.now(),
    @Column(name = "token_updated_at", nullable = false)
    override var tokenUpdatedAt: Instant = Instant.now(),
    @Column(name = "token_subtype") override var tokenSubtype: String? = null,
    @Column(name = "last_token_hash") override var lastToken: String? = null,
    @Column(name = "previous_token_hash") override var previousToken: String? = null,
    @Column(name = "last_used_at") override var lastUsedAt: Instant? = null,
) : OgiriToken {
  @Transient override var plainToken: String? = null
}
