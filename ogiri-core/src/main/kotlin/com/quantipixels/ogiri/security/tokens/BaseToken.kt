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
 * Table-agnostic base token abstraction.
 *
 * This abstract class defines the contract for all token implementations without imposing any
 * database-specific dependencies or annotations.
 *
 * Users should extend this class and provide their own implementations of required properties using
 * their preferred persistence mechanism:
 * - JPA entities with @Entity, @Column, etc.
 * - JDBC data classes with manual SQL
 * - MongoDB @Document classes
 * - Redis data classes
 * - Custom implementations
 *
 * Example - JPA Implementation:
 * ```kotlin
 * @Entity
 * @Table(name = "user_tokens")
 * data class JpaToken(
 *   @Id @GeneratedValue override val id: Long = 0,
 *   @Column(name = "user_id") override val userId: Long,
 *   @Column(name = "client_id") override val client: String,
 *   @Column(name = "token_hash") override val token: String,
 *   // ... remaining fields
 * ) : BaseToken()
 * ```
 *
 * Example - JDBC/Plain Kotlin:
 * ```kotlin
 * data class JdbcToken(
 *   override val id: Long = 0,
 *   override val userId: Long,
 *   override val client: String,
 *   override val token: String,
 *   // ... remaining fields
 * ) : BaseToken()
 * ```
 */
abstract class BaseToken {
  /** Unique token identifier (primary key). Database auto-increment recommended. */
  abstract val id: Long

  /** User identifier associated with this token. Should be indexed for efficient lookups. */
  abstract val userId: Long

  /**
   * Client/application identifier. Combined with userId for unique constraint. Should be indexed.
   */
  abstract val client: String

  /**
   * Hashed token value (never plaintext). Use BCrypt or similar hashing algorithm. Always stored in
   * database.
   */
  abstract var token: String

  /**
   * Token type classifier. Default: "APP" for primary tokens. Custom: "device", "chat", etc. for
   * sub-tokens.
   */
  abstract val tokenType: String

  /**
   * Token expiration timestamp (UTC). Should be indexed for efficient cleanup of expired tokens.
   */
  abstract var expiryAt: Instant

  /**
   * Timestamp when token was created. Usually auto-populated (Instant.now() or database DEFAULT).
   * Not updatable after creation.
   */
  abstract val createdAt: Instant

  /** Timestamp when token was last updated. Usually auto-updated on any row modification. */
  abstract val updatedAt: Instant

  /** Timestamp when token rotation last occurred. Used for token rotation policy decisions. */
  abstract var tokenUpdatedAt: Instant

  /**
   * Optional sub-token identifier. Used to distinguish different token types within same token
   * record. Example: "device", "chat", "api"
   */
  open var tokenSubtype: String? = null

  /**
   * Previous token hash (for grace period during rotation). Allows brief window where old token
   * still works while new token is issued.
   */
  open var lastToken: String? = null

  /**
   * Token before last (extended grace period). For additional safety during token rotation cascade.
   */
  open var previousToken: String? = null

  /**
   * Last timestamp this token was successfully used for authentication. Useful for monitoring and
   * cleanup of stale tokens.
   */
  open var lastUsedAt: Instant? = null

  /**
   * Plain (unhashed) token value. NEVER persisted to database. Only exists in-memory temporarily
   * during token creation. Sent to client for authentication headers.
   */
  var plainToken: String? = null

  /**
   * Check if token has expired.
   *
   * @param now Current instant for comparison (defaults to Instant.now())
   * @return true if expiryAt is before now, false otherwise
   */
  fun isExpired(now: Instant = Instant.now()): Boolean = expiryAt.isBefore(now)

  override fun toString(): String =
      "Token(id=$id, userId=$userId, client=$client, tokenType=$tokenType, expiryAt=$expiryAt)"
}
