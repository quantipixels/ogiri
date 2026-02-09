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
import java.util.Optional
import org.springframework.data.repository.NoRepositoryBean

/**
 * Repository interface for token persistence.
 *
 * Method names follow Spring Data naming conventions for automatic query generation. Extend this
 * interface along with your preferred Spring Data repository (JpaRepository, CrudRepository, etc.)
 * and Spring Data will generate all implementations automatically.
 *
 * ## Example - Direct Spring Data Integration (Recommended):
 * ```kotlin
 * @Repository
 * interface MyTokenRepository : JpaRepository<MyToken, Long>, OgiriTokenRepository<MyToken>
 * ```
 *
 * When extending Spring Data repositories, the standard CRUD operations (`save`, `findById`,
 * `delete`, `deleteById`) are provided by Spring Data. This interface only declares the
 * token-specific query methods that Spring Data can auto-generate from method names.
 *
 * ## Example - Custom Implementation (non-Spring Data):
 * ```kotlin
 * @Repository
 * class JdbcTokenRepository(private val jdbcTemplate: JdbcTemplate) : OgiriTokenRepository<JdbcToken> {
 *   // Implement CRUD methods
 *   override fun save(token: JdbcToken): JdbcToken { /* INSERT or UPDATE logic */ }
 *   override fun findById(id: Long): Optional<JdbcToken> { /* ... */ }
 *   override fun delete(token: JdbcToken) { /* ... */ }
 *   override fun deleteById(id: Long) { /* ... */ }
 *   // ... implement query methods
 * }
 * ```
 *
 * All methods must handle null/empty return values appropriately for the implementation.
 * Implementers should ensure:
 * - Thread-safety for concurrent access
 * - Transaction semantics if applicable to the storage backend
 * - Proper error handling for storage failures
 */
@NoRepositoryBean
interface OgiriTokenRepository<T : OgiriToken> {

  /**
   * Persist a token entity, inserting or updating as appropriate.
   *
   * @param token The token to persist.
   * @return The saved token; for newly inserted tokens this includes the generated ID.
   */
  fun <S : T> save(token: S): S

  /**
   * Retrieve a token by its primary key ID.
   *
   * @param id The token ID
   * @return Optional containing the token if found, empty otherwise
   */
  fun findById(id: Long): Optional<T>

  /**
   * Delete a token by its primary key ID.
   *
   * @param id The token ID to delete
   */
  fun deleteById(id: Long)

  /**
   * Delete the given token.
   *
   * @param token The token to delete.
   */
  fun delete(token: T)

  /**
   * Find all tokens for a user, ordered by most recently updated first.
   *
   * Used for listing user sessions and cleanup operations.
   *
   * @param userId The ID of the user whose tokens to retrieve.
   * @return List of tokens for the user; empty list if none are found.
   */
  fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>

  /**
   * Find a specific token by user and client.
   *
   * Used for session lookup and token rotation.
   *
   * @param userId The user's primary key ID.
   * @param client The client/application identifier.
   * @return Optional containing the token if found, empty otherwise.
   */
  fun findByUserIdAndClient(
      userId: Long,
      client: String,
  ): Optional<T>

  /**
   * Find tokens for a user matching any of the given clients.
   *
   * Used for batch loading sub-tokens to avoid N+1 queries. Spring Data will auto-generate this
   * query from the method name.
   *
   * @param userId The user's primary key ID.
   * @param clients Collection of client identifiers to match.
   * @return List of tokens matching any of the clients; empty list if none found.
   */
  fun findByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  ): List<T>

  /**
   * Find tokens for a user filtered by subtype (e.g., "device", "api"), ordered by most recently
   * updated first.
   *
   * Used for sub-token management.
   *
   * @param userId The ID of the user whose tokens to retrieve.
   * @param tokenSubtype Sub-token type identifier (for example `"device"` or `"chat"`).
   * @return List of matching tokens; an empty list when none are found.
   */
  fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<T>

  /**
   * Find all tokens that expired before the cutoff.
   *
   * Used by cleanup job to identify expired tokens.
   *
   * @param cutoff Instant used as the expiry threshold; tokens with an expiry time before this
   *   Instant are returned.
   * @return List of tokens that expired before the cutoff, or an empty list if none are found.
   */
  fun findByExpiryAtBefore(cutoff: Instant): List<T>

  /**
   * Find all tokens of a specific type (e.g., "app", "sub").
   *
   * Used for prefix-less token lookups.
   *
   * @param tokenType The token type to filter by (e.g., "app", "sub")
   * @return List of tokens matching the type
   */
  fun findByTokenType(tokenType: String): List<T>

  /**
   * Delete a specific user's token for a client.
   *
   * Used for single-device logout.
   *
   * @param userId The user ID
   * @param client The client/application identifier
   */
  fun deleteByUserIdAndClient(
      userId: Long,
      client: String,
  )

  /**
   * Delete multiple tokens for a user by client IDs.
   *
   * Used for bulk session revocation (e.g., revoke all devices except the current one).
   *
   * @param userId The user's primary key identifier.
   * @param clients Collection of client identifiers whose tokens should be removed for the user.
   */
  fun deleteByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  )

  /**
   * Delete all tokens for a user.
   *
   * Used for global logout and account deletion.
   *
   * @param userId The user ID
   */
  fun deleteByUserId(userId: Long)

  /**
   * Count tokens for a user.
   *
   * Spring Data auto-generates a COUNT query from the method name. For custom implementations,
   * override with an efficient COUNT query:
   * ```kotlin
   * @Query("SELECT COUNT(t) FROM Token t WHERE t.userId = :userId")
   * override fun countByUserId(userId: Long): Long
   * ```
   *
   * @param userId The user ID to count tokens for
   * @return Number of tokens belonging to the user
   */
  fun countByUserId(userId: Long): Long

  /**
   * Delete all expired tokens before cutoff.
   *
   * Spring Data auto-generates a DELETE query from the method name. For custom implementations,
   * override with a bulk DELETE for performance:
   * ```kotlin
   * @Modifying
   * @Query("DELETE FROM Token t WHERE t.expiryAt < :cutoff")
   * override fun deleteByExpiryAtBefore(cutoff: Instant): Int
   * ```
   *
   * @param cutoff Instant used as the expiry threshold; tokens expiring before this are deleted.
   * @return Number of tokens deleted.
   */
  fun deleteByExpiryAtBefore(cutoff: Instant): Int
}
