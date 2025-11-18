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
package com.quantipixels.ogiri.security.tokens

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@MappedSuperclass
open class BaseToken(
  @Column(name = "user_id", nullable = false) open var userId: Long,
  @Column(name = "client_id", nullable = false) open var client: String,
  @Column(name = "token_hash", nullable = false) open var token: String,
  @Column(name = "token_type", nullable = false) open var tokenType: TokenType = TokenType.APP,
  @Column(name = "expiry_at", nullable = false) open var expiryAt: Instant,
  @Column(name = "last_token_hash") open var lastToken: String? = null,
  @Column(name = "previous_token_hash") open var previousToken: String? = null,
  @Column(name = "token_subtype") open var tokenSubtype: String? = null,
) {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  open var id: Long = 0

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  open var createdAt: Instant = Instant.now()

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  open var updatedAt: Instant = Instant.now()

  @Column(name = "token_updated_at", nullable = false)
  open var tokenUpdatedAt: Instant = Instant.now()

  @Column(name = "last_used_at")
  open var lastUsedAt: Instant? = null

  @Transient
  open var plainToken: String? = null

  fun isExpired(now: Instant = Instant.now()): Boolean = expiryAt.isBefore(now)
}

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
        columnNames = ["user_id", "client_id"],
      ),
    ],
)
open class Token(
  userId: Long,
  client: String,
  token: String,
  tokenType: TokenType = TokenType.APP,
  expiryAt: Instant,
  lastToken: String? = null,
  previousToken: String? = null,
  tokenSubtype: String? = null,
) :
  BaseToken(
      userId = userId,
      client = client,
      token = token,
      tokenType = tokenType,
      expiryAt = expiryAt,
      lastToken = lastToken,
      previousToken = previousToken,
      tokenSubtype = tokenSubtype,
    )
