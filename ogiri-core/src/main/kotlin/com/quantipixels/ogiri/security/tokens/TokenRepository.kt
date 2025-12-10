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
 * Pure interface for token persistence - database-agnostic contract.
 *
 * This interface defines the contract for token storage without imposing any database-specific
 * dependencies or assumptions. Users implement this interface with their chosen persistence
 * mechanism:
 * - JPA repositories (using Spring Data JPA)
 * - JDBC data access (using JdbcTemplate)
 * - MongoDB repositories (using MongoTemplate)
 * - Redis clients (using Lettuce or Jedis)
 * - Custom implementations (file-based, in-memory, etc.)
 *
 * All methods must handle null/empty return values appropriately for the implementation.
 * Implementers should ensure:
 * - Thread-safety for concurrent access
 * - Transaction semantics if applicable to the storage backend
 * - Proper error handling for storage failures
 *
 * Example - JPA Implementation:
 * ```kotlin
 * @Repository
 * interface JpaTokenRepository : JpaRepository<MyToken, Long>, TokenRepository<MyToken> {
 *   override fun findAllByUserId(userId: Long): List<MyToken> =
 *     findAllByUserIdOrderByUpdatedAtDesc(userId)
 *
 *   fun findAllByUserIdOrderByUpdatedAtDesc(userId: Long): List<MyToken>
 * }
 * ```
 *
 * Example - JDBC Implementation:
 * ```kotlin
 * @Repository
 * class JdbcTokenRepository(private val jdbcTemplate: JdbcTemplate) : TokenRepository<JdbcToken> {
 *   override fun save(token: JdbcToken): JdbcToken {
 *     // INSERT or UPDATE logic
 *   }
 * }
 * ```
 */
interface TokenRepository<T : BaseToken> {
  /**
   * Save or update a token entity.
   *
   * If the token has id == 0, this should insert and return token with generated ID. If the token
   * has id > 0, this should update the existing row.
   *
   * @param token The token to persist
   * @return The saved token (with ID if newly inserted)
   */
  fun save(token: T): T

  /**
   * Retrieve a token by its primary key ID.
   *
   * @param id The token ID
   * @return The token if found, null otherwise
   */
  fun findById(id: Long): T?

  /**
   * Delete a token by its primary key ID.
   *
   * @param id The token ID to delete
   */
  fun deleteById(id: Long)

  /**
   * Find all tokens for a specific user.
   *
   * Typically ordered by updated_at DESC to get most recent first.
   *
   * @param userId The user ID
   * @return List of tokens (empty if none found)
   */
  fun findAllByUserId(userId: Long): List<T>

  /**
   * Find all tokens for a user with a specific subtype.
   *
   * This is required by the core helpers when querying for sub-tokens, but feels optional to the
   * persistence layer because implementations may want to define their own optimized query (e.g., a
   * Spring Data JPA derived method). Examples:
   * - `List<MyToken> findAllByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(Long userId, String
   *   subtype)`
   * - `SELECT * FROM tokens WHERE user_id = ? AND token_subtype = ? ORDER BY updated_at DESC`
   *
   * @param userId The user ID
   * @param tokenSubtype The sub-token type identifier (e.g., "device", "chat")
   * @return List of tokens for that subtype (empty when none exist)
   */
  fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<T>

  /**
   * Find the token for a specific user and client combination.
   *
   * This is the primary lookup for validating incoming requests. Users have one token per client.
   *
   * @param userId The user ID
   * @param clientId The client/application identifier
   * @return The token if found, null otherwise
   */
  fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): T?

  /**
   * Find all tokens that have expired before a specific cutoff time.
   *
   * Used by TokenCleanupJob to identify and remove stale tokens.
   *
   * @param cutoff The expiry time threshold (tokens before this are expired)
   * @return List of expired tokens (empty if none found)
   */
  fun findByExpiryAtBefore(cutoff: Instant): List<T>

  /**
   * Delete the token for a specific user and client.
   *
   * This is typically called during logout or client revocation.
   *
   * @param userId The user ID
   * @param clientId The client/application identifier
   */
  fun deleteByUserIdAndClient(
      userId: Long,
      clientId: String,
  )

  /**
   * Delete tokens for a specific user and multiple clients.
   *
   * Used to revoke multiple tokens at once (e.g., revoke all devices except current).
   *
   * @param userId The user ID
   * @param clientIds Collection of client identifiers to delete
   */
  fun deleteByUserIdAndClientIn(
      userId: Long,
      clientIds: Collection<String>,
  )

  /**
   * Delete all tokens for a specific user.
   *
   * Called during account deletion or global logout.
   *
   * @param userId The user ID
   */
  fun deleteByUserId(userId: Long)

  /**
   * Delete a token by primary key (generic helper).
   *
   * @param token The token to delete
   */
  fun delete(token: T) {
    token.id.takeIf { it > 0 }?.let { deleteById(it) }
  }
}
