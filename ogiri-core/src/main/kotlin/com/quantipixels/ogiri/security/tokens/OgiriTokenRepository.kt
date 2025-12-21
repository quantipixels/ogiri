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
 * interface JpaTokenRepository : JpaRepository<MyToken, Long>, OgiriTokenRepository<MyToken> {
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
 * class JdbcTokenRepository(private val jdbcTemplate: JdbcTemplate) : OgiriTokenRepository<JdbcToken> {
 *   override fun save(token: JdbcToken): JdbcToken {
 *     // INSERT or UPDATE logic
 *   }
 * }
 * ```
 */
interface OgiriTokenRepository<T : OgiriToken> {
  /**
   * Persist a token entity, inserting or updating as appropriate.
   *
   * If the token's `id` is 0, insert a new record and return the token with the generated ID. If
   * the token's `id` is greater than 0, update the existing record and return the updated token.
   *
   * @param token The token to persist.
   * @return The saved token; for newly inserted tokens this includes the generated ID.
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
   * Retrieve all tokens belonging to the given user.
   *
   * Implementations typically return results ordered by `updated_at` descending.
   *
   * @return List of tokens for the user; empty list if none are found.
   */
  fun findAllByUserId(userId: Long): List<T>

  /**
   * Retrieve all tokens for a user filtered by the given token subtype, ordered by most recently
   * updated first.
   *
   * @param userId The ID of the user whose tokens to retrieve.
   * @param tokenSubtype Sub-token type identifier (for example `"device"` or `"chat"`).
   * @return List of matching tokens; an empty list when none are found.
   */
  fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<T>

  /**
   * Retrieve the token associated with the given user and client.
   *
   * @param userId The user's primary key ID.
   * @param clientId The client/application identifier.
   * @return The token if found, `null` otherwise.
   */
  fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): T?

  /**
   * Retrieve tokens whose expiry timestamp is strictly before the given cutoff.
   *
   * @param cutoff Instant used as the expiry threshold; tokens with an expiry time before this
   *   Instant are returned.
   * @return List of tokens that expired before the cutoff, or an empty list if none are found.
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
   * Deletes tokens for a specific user that belong to any of the given clients.
   *
   * Typical use: revoke multiple client sessions for a user (for example, revoke all devices except
   * the current one).
   *
   * @param userId The user's primary key identifier.
   * @param clientIds Collection of client identifiers whose tokens should be removed for the user.
   */
  fun deleteByUserIdAndClientIn(
      userId: Long,
      clientIds: Collection<String>,
  )

  /**
   * Delete all tokens associated with the given user.
   *
   * Called during account deletion or global logout.
   */
  fun deleteByUserId(userId: Long)

  /**
   * Delete the given token when its id is greater than zero; no action otherwise.
   *
   * @param token The token whose id will be used to perform deletion.
   */
  fun delete(token: T) {
    token.id.takeIf { it > 0 }?.let { deleteById(it) }
  }
}
