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
package com.quantipixels.ogiri.jpa

import com.quantipixels.ogiri.security.tokens.OgiriBaseToken
import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Transient
import java.time.Instant

/**
 * Base JPA entity with all required Ogiri token fields pre-configured.
 *
 * This @MappedSuperclass provides a complete JPA implementation of OgiriToken with all fields,
 * column mappings, and lifecycle callbacks. Users extend this class and add @Entity + @Table:
 *
 * Example usage:
 * ```kotlin
 * @Entity
 * @Table(name = "tokens")
 * class Token : OgiriBaseTokenEntity()
 * ```
 *
 * All 15+ fields with proper JPA annotations are inherited:
 * - id (auto-generated primary key)
 * - userId, client, token, tokenType
 * - expiryAt, createdAt, updatedAt, tokenUpdatedAt
 * - lastUsedAt, previousToken, lastToken, tokenSubtype
 * - plainToken (transient, not persisted)
 *
 * Benefits:
 * - Eliminates manual entity creation with 15+ fields and annotations
 * - Provides automatic timestamp management (createdAt, updatedAt)
 * - Ensures consistent column naming and constraints
 * - Allows focus on business logic instead of JPA boilerplate
 *
 * Advanced customization:
 * ```kotlin
 * @Entity
 * @Table(
 *     name = "auth_tokens",
 *     indexes = [
 *         Index(name = "idx_user_client", columnList = "user_id,client", unique = true),
 *         Index(name = "idx_expiry", columnList = "expiry_at")
 *     ]
 * )
 * class CustomToken : OgiriBaseTokenEntity() {
 *     // Add custom fields if needed
 *     @Column(name = "metadata")
 *     var metadata: String? = null
 * }
 * ```
 */
@MappedSuperclass
abstract class OgiriBaseTokenEntity : OgiriBaseToken() {

  /**
   * Unique token identifier (primary key).
   *
   * Auto-generated using IDENTITY strategy (database auto-increment). This is the most portable
   * strategy across different databases (MySQL, PostgreSQL, H2, etc.).
   */
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) override var id: Long = 0

  /**
   * User identifier associated with this token.
   *
   * Foreign key to your User table. Should be indexed for efficient lookups. Combined with client
   * for unique constraint.
   */
  @Column(name = "user_id", nullable = false) override var userId: Long = 0

  /**
   * Client/application identifier.
   *
   * Identifies the device, browser, or application instance. Examples: "web-chrome-desktop",
   * "mobile-ios-app", "api-client-xyz". Combined with userId for unique constraint.
   */
  @Column(name = "client", nullable = false, length = 64) override var client: String = ""

  /**
   * Hashed token value (BCrypt hash of the actual token).
   *
   * NEVER stores plaintext tokens. The actual token is generated, hashed with BCrypt, and only the
   * hash is persisted. Max length 512 to accommodate BCrypt hashes and future algorithm changes.
   */
  @Column(name = "token", nullable = false, length = 512) override var token: String = ""

  /**
   * Token type classifier.
   *
   * Default: "APP" for primary authentication tokens. Custom values for sub-tokens: "device",
   * "chat", "api", etc. Used to distinguish token purposes and apply different expiry policies.
   */
  @Column(name = "token_type", nullable = false, length = 16) override var tokenType: String = "APP"

  /**
   * Token expiration timestamp (UTC).
   *
   * When the token becomes invalid and should no longer be accepted. Should be indexed for
   * efficient cleanup queries. Tokens are deleted by OgiriTokenCleanupJob after expiration.
   */
  @Column(name = "expiry_at", nullable = false) override var expiryAt: Instant = Instant.now()

  /**
   * Timestamp when token was created (UTC).
   *
   * Auto-populated on first save via @PrePersist. Immutable after creation. Useful for auditing and
   * analytics.
   */
  @Column(name = "created_at", nullable = false, updatable = false)
  override var createdAt: Instant = Instant.now()

  /**
   * Timestamp when token was last updated (UTC).
   *
   * Auto-updated on every save via @PreUpdate and @PrePersist. Tracks any modification to the
   * token record.
   */
  @Column(name = "updated_at", nullable = false) override var updatedAt: Instant = Instant.now()

  /**
   * Timestamp when token rotation last occurred (UTC).
   *
   * Updated when the token hash is rotated (new token generated). Used by rotation policies to
   * determine if forced rotation is needed (e.g., rotate tokens older than X hours).
   */
  @Column(name = "token_updated_at", nullable = false)
  override var tokenUpdatedAt: Instant = Instant.now()

  /**
   * Last timestamp this token was successfully used for authentication (UTC).
   *
   * Updated on every successful authentication. Useful for: - Monitoring token usage patterns -
   * Identifying stale/unused tokens - Implementing "last active" features
   */
  @Column(name = "last_used_at") override var lastUsedAt: Instant? = null

  /**
   * Previous token hash (for grace period during rotation).
   *
   * When a token is rotated, the old hash is moved here. This allows a brief grace period where
   * both the new token and the previous token are accepted, preventing race conditions during token
   * rotation.
   */
  @Column(name = "previous_token", length = 512) override var previousToken: String? = null

  /**
   * Token before last (extended grace period).
   *
   * Stores the token hash from two rotations ago. Provides an extended grace period for token
   * rotation cascades (old -> last -> previous).
   */
  @Column(name = "last_token", length = 512) override var lastToken: String? = null

  /**
   * Optional sub-token identifier.
   *
   * Used to distinguish different token types within the same token record. Example: "device",
   * "chat", "api". Allows querying tokens by subtype for selective revocation or management.
   */
  @Column(name = "token_subtype", length = 32) override var tokenSubtype: String? = null

  /**
   * Plain (unhashed) token value. NEVER persisted to database.
   *
   * Marked @Transient to exclude from database persistence. Only exists in-memory temporarily
   * during token creation. Used to send the actual token to the client in authentication responses.
   */
  @Transient override var plainToken: String? = null

  /**
   * Lifecycle callback: Update timestamps before insert.
   *
   * Ensures createdAt and updatedAt are set to current time on first save.
   */
  @PrePersist
  fun onCreate() {
    val now = Instant.now()
    createdAt = now
    updatedAt = now
  }

  /**
   * Lifecycle callback: Update timestamp before update.
   *
   * Ensures updatedAt is refreshed to current time on every update.
   */
  @PreUpdate
  fun onUpdate() {
    updatedAt = Instant.now()
  }
}
