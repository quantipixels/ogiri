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

import com.quantipixels.ogiri.security.tokens.OgiriToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Base adapter that implements OgiriTokenRepository using Spring Data JPA.
 *
 * This abstract class eliminates approximately 80 lines of boilerplate code by providing standard
 * CRUD operations and delegating custom queries to the implementing class. Users only need to:
 * 1. Create their JPA repository interface extending JpaRepository
 * 2. Extend this class and provide custom query method delegations
 *
 * The adapter implements the standard repository methods (save, findById, delete) while requiring
 * subclasses to implement database-specific queries that Spring Data JPA cannot automatically
 * generate from the OgiriTokenRepository interface.
 *
 * Example usage:
 * ```kotlin
 * // 1. Define your JPA repository interface with custom queries
 * @Repository
 * interface TokenJpaRepository : JpaRepository<Token, Long> {
 *   fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<Token>
 *   fun findByUserIdAndClient(userId: Long, client: String): Token?
 *   fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<Token>
 *   fun findByExpiryAtBefore(cutoff: Instant): List<Token>
 *   fun deleteByUserIdAndClient(userId: Long, client: String)
 *   fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>)
 *   fun deleteByUserId(userId: Long)
 * }
 *
 * // 2. Create adapter that extends this class and delegates to JPA methods
 * @Repository
 * class TokenRepositoryAdapter(
 *     jpa: TokenJpaRepository
 * ) : AbstractJpaTokenRepositoryAdapter<Token, TokenJpaRepository>(jpa) {
 *
 *   override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
 *       jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
 *
 *   override fun findByUserIdAndClientEquals(userId: Long, client: String) =
 *       jpaRepository.findByUserIdAndClient(userId, client)
 *
 *   override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String) =
 *       jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)
 *
 *   override fun findByExpiryAtBeforeCutoff(cutoff: Instant) =
 *       jpaRepository.findByExpiryAtBefore(cutoff)
 *
 *   override fun deleteByUserIdAndClientEquals(userId: Long, client: String) =
 *       jpaRepository.deleteByUserIdAndClient(userId, client)
 *
 *   override fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>) =
 *       jpaRepository.deleteByUserIdAndClientIn(userId, clientIds)
 *
 *   override fun deleteByUserIdJpa(userId: Long) =
 *       jpaRepository.deleteByUserId(userId)
 * }
 * ```
 *
 * Benefits:
 * - Reduces implementation from ~80 lines to ~15 lines
 * - Provides type-safe delegation to JPA repository
 * - Maintains clear separation between OgiriTokenRepository contract and JPA implementation
 * - Allows customization of query method names in JPA repository
 *
 * @param T The token entity type that implements OgiriToken
 * @param R The JPA repository type extending JpaRepository<T, Long>
 * @property jpaRepository The underlying Spring Data JPA repository
 */
abstract class AbstractJpaTokenRepositoryAdapter<T : OgiriToken, R : JpaRepository<T, Long>>(
    protected val jpaRepository: R
) : OgiriTokenRepository<T> {

  // ==================== Standard CRUD operations (implemented) ====================

  /**
   * Save or update a token entity.
   *
   * Delegates to JpaRepository.save() which handles both insert and update operations.
   */
  override fun save(token: T): T = jpaRepository.save(token)

  /**
   * Find a token by its primary key ID.
   *
   * Delegates to JpaRepository.findById() and unwraps the Optional.
   */
  override fun findById(id: Long): T? = jpaRepository.findById(id).orElse(null)

  /**
   * Delete a token by its primary key ID.
   *
   * Delegates to JpaRepository.deleteById().
   */
  override fun deleteById(id: Long) = jpaRepository.deleteById(id)

  /**
   * Delete a token entity.
   *
   * Delegates to JpaRepository.delete().
   */
  override fun delete(token: T) = jpaRepository.delete(token)

  // ==================== Abstract methods for custom queries ====================

  /**
   * Find all tokens for a user, ordered by updatedAt descending.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>
   * ```
   *
   * @param userId The user ID
   * @return List of tokens ordered by most recently updated
   */
  abstract fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>

  /**
   * Find the token for a specific user and client combination.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun findByUserIdAndClient(userId: Long, client: String): T?
   * ```
   *
   * @param userId The user ID
   * @param client The client identifier
   * @return The token if found, null otherwise
   */
  abstract fun findByUserIdAndClientEquals(userId: Long, client: String): T?

  /**
   * Find all tokens for a user with a specific subtype, ordered by updatedAt descending.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<T>
   * ```
   *
   * @param userId The user ID
   * @param subtype The token subtype
   * @return List of tokens for that subtype
   */
  abstract fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      subtype: String
  ): List<T>

  /**
   * Find all tokens that have expired before a cutoff time.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun findByExpiryAtBefore(cutoff: Instant): List<T>
   * ```
   *
   * @param cutoff The expiry time threshold
   * @return List of expired tokens
   */
  abstract fun findByExpiryAtBeforeCutoff(cutoff: Instant): List<T>

  /**
   * Delete the token for a specific user and client.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun deleteByUserIdAndClient(userId: Long, client: String)
   * ```
   *
   * @param userId The user ID
   * @param client The client identifier
   */
  abstract fun deleteByUserIdAndClientEquals(userId: Long, client: String)

  /**
   * Delete tokens for a user matching multiple client IDs.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>)
   * ```
   *
   * @param userId The user ID
   * @param clientIds Collection of client identifiers to delete
   */
  abstract fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>)

  /**
   * Delete all tokens for a specific user.
   *
   * Note: Named "deleteByUserIdJpa" to avoid naming conflicts with OgiriTokenRepository.deleteByUserId.
   *
   * Implementing classes must delegate to a JPA repository method like:
   * ```kotlin
   * fun deleteByUserId(userId: Long)
   * ```
   *
   * @param userId The user ID
   */
  abstract fun deleteByUserIdJpa(userId: Long)

  // ==================== OgiriTokenRepository implementations (delegate to abstract) ====================

  /**
   * Find all tokens for a user.
   *
   * Delegates to findByUserIdOrderByUpdatedAtDesc.
   */
  override fun findAllByUserId(userId: Long): List<T> = findByUserIdOrderByUpdatedAtDesc(userId)

  /**
   * Find the token for a user and client.
   *
   * Delegates to findByUserIdAndClientEquals.
   */
  override fun findByUserIdAndClient(userId: Long, clientId: String): T? =
      findByUserIdAndClientEquals(userId, clientId)

  /**
   * Find all tokens for a user with a specific subtype.
   *
   * Delegates to findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc.
   */
  override fun findAllByUserIdAndTokenSubtype(userId: Long, tokenSubtype: String): List<T> =
      findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)

  /**
   * Find all expired tokens before a cutoff time.
   *
   * Delegates to findByExpiryAtBeforeCutoff.
   */
  override fun findByExpiryAtBefore(cutoff: Instant): List<T> = findByExpiryAtBeforeCutoff(cutoff)

  /**
   * Delete the token for a user and client.
   *
   * Delegates to deleteByUserIdAndClientEquals.
   */
  override fun deleteByUserIdAndClient(userId: Long, clientId: String) =
      deleteByUserIdAndClientEquals(userId, clientId)

  /**
   * Delete tokens for a user matching multiple clients.
   *
   * Delegates to deleteByUserIdAndClientIdIn.
   */
  override fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>) =
      deleteByUserIdAndClientIdIn(userId, clientIds)

  /**
   * Delete all tokens for a user.
   *
   * Delegates to deleteByUserIdJpa.
   */
  override fun deleteByUserId(userId: Long) = deleteByUserIdJpa(userId)
}
