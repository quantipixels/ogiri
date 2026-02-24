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

import java.time.Instant

/**
 * Core interface for all Ogiri token implementations.
 *
 * This interface defines the contract for token entities without imposing implementation
 * constraints. Users can:
 * - Implement this interface directly for maximum flexibility
 * - Extend [OgiriBaseToken] for a convenience implementation with sensible defaults
 *
 * Example - Direct Interface Implementation:
 * ```kotlin
 * @Entity
 * @Table(name = "my_tokens")
 * data class MyToken(
 *     @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
 *     override val id: Long = 0,
 *     @Column(name = "user_id") override val userId: Long,
 *     @Column(name = "client_id") override val client: String,
 *     @Column(name = "token_hash") override var token: String,
 *     @Column(name = "token_type") override val tokenType: String = "app",
 *     @Column(name = "expiry_at") override var expiryAt: Instant,
 *     @CreationTimestamp override val createdAt: Instant = Instant.now(),
 *     @UpdateTimestamp override val updatedAt: Instant = Instant.now(),
 *     @Column(name = "token_updated_at") override var tokenUpdatedAt: Instant = Instant.now(),
 *     @Column(name = "token_subtype") override var tokenSubtype: String? = null,
 *     @Column(name = "last_token_hash") override var lastToken: String? = null,
 *     @Column(name = "previous_token_hash") override var previousToken: String? = null,
 *     @Column(name = "last_used_at") override var lastUsedAt: Instant? = null,
 *     // Custom fields for your application
 *     @Column(name = "metadata") var metadata: String? = null,
 * ) : OgiriToken {
 *     @Transient
 *     override var plainToken: String? = null
 * }
 * ```
 *
 * Example - Using Base Class:
 * ```kotlin
 * @Entity
 * data class MyToken(
 *     override val id: Long = 0,
 *     override val userId: Long,
 *     // ... remaining fields
 * ) : OgiriBaseToken()
 * ```
 */
interface OgiriToken {
  /** Unique token identifier (primary key). Database auto-increment recommended. */
  val id: Long

  /** User identifier associated with this token. Should be indexed for efficient lookups. */
  val userId: Long

  /**
   * Client/application identifier. Combined with userId for unique constraint. Should be indexed.
   *
   * The (userId, client) pair is the primary lookup key — each user has at most one token per
   * client. This means writes are single-writer per key: no two writers can race for the same
   * user/client record.
   */
  val client: String

  /**
   * Hashed token value (never plaintext). Use BCrypt or similar hashing algorithm. Always stored in
   * database.
   */
  var token: String

  /**
   * Token type classifier. Default: "app" for primary tokens. Use "sub" for specialized tokens
   * (device, chat, etc.).
   */
  val tokenType: String

  /**
   * Token expiration timestamp (UTC). Should be indexed for efficient cleanup of expired tokens.
   */
  var expiryAt: Instant

  /**
   * Timestamp when token was created. Usually auto-populated (Instant.now() or database DEFAULT).
   * Not updatable after creation.
   */
  val createdAt: Instant

  /** Timestamp when token was last updated. Usually auto-updated on any row modification. */
  val updatedAt: Instant

  /** Timestamp when token rotation last occurred. Used for token rotation policy decisions. */
  var tokenUpdatedAt: Instant

  /**
   * Optional sub-token identifier. Used to distinguish different token types within same token
   * record. Example: "device", "chat", "api"
   */
  var tokenSubtype: String?

  /**
   * Previous token hash (for grace period during rotation). Allows brief window where old token
   * still works while new token is issued.
   */
  var lastToken: String?

  /**
   * Token before last (extended grace period). For additional safety during token rotation cascade.
   */
  var previousToken: String?

  /**
   * Last timestamp this token was successfully used for authentication. Useful for monitoring and
   * cleanup of stale tokens.
   */
  var lastUsedAt: Instant?

  /**
   * Plain (unhashed) token value. NEVER persisted to database. NEVER logged. Only exists in-memory
   * temporarily during token creation. Sent to client for authentication headers.
   *
   * Implementations using JPA/Hibernate should mark this with @Transient.
   */
  var plainToken: String?

  /**
   * Determines whether the token is expired relative to the provided instant.
   *
   * @param now Instant to compare the token's expiry against; defaults to the current instant.
   * @return `true` if `expiryAt` is before `now`, `false` otherwise.
   */
  fun isExpired(now: Instant = Instant.now()): Boolean = expiryAt.isBefore(now)
}
