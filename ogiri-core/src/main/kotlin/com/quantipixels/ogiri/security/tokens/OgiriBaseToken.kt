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
 * Convenience abstract base class implementing [OgiriToken].
 *
 * This abstract class provides a convenient base implementation with sensible defaults for optional
 * properties. Users who prefer class inheritance can extend this class. Users who need more
 * flexibility (e.g., to extend their own base class) can implement [OgiriToken] directly.
 *
 * Example - JPA Implementation:
 * ```kotlin
 * @Entity
 * @Table(name = "user_tokens")
 * data class JpaToken(
 *   @Id @GeneratedValue override val id: Long = 0,
 *   @Column(name = "user_id") override val userId: Long,
 *   @Column(name = "client_id") override val client: String,
 *   @Column(name = "token_hash") override var token: String,
 *   // ... remaining fields
 * ) : OgiriBaseToken()
 * ```
 *
 * Example - JDBC/Plain Kotlin:
 * ```kotlin
 * data class JdbcToken(
 *   override val id: Long = 0,
 *   override val userId: Long,
 *   override val client: String,
 *   override var token: String,
 *   // ... remaining fields
 * ) : OgiriBaseToken()
 * ```
 */
abstract class OgiriBaseToken : OgiriToken {
  /** Unique token identifier (primary key). Database auto-increment recommended. */
  abstract override var id: Long

  /** User identifier associated with this token. Should be indexed for efficient lookups. */
  abstract override val userId: Long

  /**
   * Client/application identifier. Combined with userId for unique constraint. Should be indexed.
   */
  abstract override val client: String

  /**
   * Hashed token value (never plaintext). Use BCrypt or similar hashing algorithm. Always stored in
   * database.
   */
  abstract override var token: String

  /**
   * Token type classifier. Default: "app" for primary tokens. Custom: "device", "chat", etc. for
   * sub-tokens.
   */
  abstract override val tokenType: String

  /**
   * Token expiration timestamp (UTC). Should be indexed for efficient cleanup of expired tokens.
   */
  abstract override var expiryAt: Instant

  /**
   * Timestamp when token was created. Usually auto-populated (Instant.now() or database DEFAULT).
   * Not updatable after creation.
   */
  abstract override val createdAt: Instant

  /** Timestamp when token was last updated. Usually auto-updated on any row modification. */
  abstract override var updatedAt: Instant

  /** Timestamp when token rotation last occurred. Used for token rotation policy decisions. */
  abstract override var tokenUpdatedAt: Instant

  /**
   * Optional sub-token identifier. Used to distinguish different token types within same token
   * record. Example: "device", "chat", "api"
   */
  override var tokenSubtype: String? = null

  /**
   * Previous token hash (for grace period during rotation). Allows brief window where old token
   * still works while new token is issued.
   */
  override var lastToken: String? = null

  /**
   * Token before last (extended grace period). For additional safety during token rotation cascade.
   */
  override var previousToken: String? = null

  /**
   * Last timestamp this token was successfully used for authentication. Useful for monitoring and
   * cleanup of stale tokens.
   */
  override var lastUsedAt: Instant? = null

  /**
   * Plain (unhashed) token value. NEVER persisted to database. NEVER logged. Only exists in-memory
   * temporarily during token creation. Sent to client for authentication headers.
   */
  override var plainToken: String? = null

  /**
   * Produce a compact single-line representation of the token including id, userId, client,
   * tokenType, and expiryAt.
   *
   * @return A string containing a concise summary of the token's id, userId, client, tokenType, and
   *   expiryAt.
   */
  override fun toString(): String =
      "Token(id=$id, userId=$userId, client=$client, tokenType=$tokenType, expiryAt=$expiryAt)"
}
