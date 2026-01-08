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
 * Abstract base adapter that implements [OgiriTokenRepository] using Spring Data JPA.
 *
 * This adapter eliminates ~80 lines of boilerplate by providing standard implementations for common
 * operations while requiring users to implement only the custom JPA query delegations.
 *
 * ## Usage
 *
 * 1. Create your JPA repository interface with custom query methods:
 * ```kotlin
 * @Repository
 * interface MyTokenJpaRepository : JpaRepository<MyToken, Long> {
 *     fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<MyToken>
 *     fun findByUserIdAndClient(userId: Long, client: String): Optional<MyToken>
 *     fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<MyToken>
 *     fun findByExpiryAtBefore(cutoff: Instant): List<MyToken>
 *
 *     @Modifying @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
 *     fun deleteByUserIdAndClient(userId: Long, client: String)
 *
 *     @Modifying @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client IN ?2")
 *     fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>)
 *
 *     @Modifying @Query("DELETE FROM MyToken t WHERE t.userId = ?1")
 *     fun deleteByUserId(userId: Long)
 * }
 * ```
 *
 * 2. Create your adapter extending this class:
 * ```kotlin
 * @Repository
 * @Primary
 * class MyTokenRepositoryAdapter(
 *     jpaRepository: MyTokenJpaRepository
 * ) : AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(jpaRepository) {
 *
 *     override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
 *         jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
 *
 *     override fun findByUserIdAndClientEquals(userId: Long, client: String) =
 *         jpaRepository.findByUserIdAndClient(userId, client).orElse(null)
 *
 *     override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String) =
 *         jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)
 *
 *     override fun findByExpiryAtBeforeCutoff(cutoff: Instant) =
 *         jpaRepository.findByExpiryAtBefore(cutoff)
 *
 *     override fun deleteByUserIdAndClientEquals(userId: Long, client: String) =
 *         jpaRepository.deleteByUserIdAndClient(userId, client)
 *
 *     override fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>) =
 *         jpaRepository.deleteByUserIdAndClientIn(userId, clientIds)
 *
 *     override fun deleteByUserIdJpa(userId: Long) =
 *         jpaRepository.deleteByUserId(userId)
 * }
 * ```
 *
 * @param T The token entity type (must extend OgiriToken)
 * @param R The JPA repository type
 */
abstract class AbstractJpaTokenRepositoryAdapter<T : OgiriToken, R : JpaRepository<T, Long>>(
    private val jpaRepository: R,
) : OgiriTokenRepository<T> {

  /** Returns the underlying JPA repository for use in subclass implementations. */
  protected fun getJpaRepository(): R = jpaRepository

  // ========== Standard implementations (provided) ==========

  override fun save(token: T): T = jpaRepository.save(token)

  override fun findById(id: Long): T? = jpaRepository.findById(id).orElse(null)

  override fun deleteById(id: Long) = jpaRepository.deleteById(id)

  override fun delete(token: T) = jpaRepository.delete(token)

  // ========== Delegating implementations ==========

  override fun findAllByUserId(userId: Long): List<T> = findByUserIdOrderByUpdatedAtDesc(userId)

  override fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): T? = findByUserIdAndClientEquals(userId, clientId)

  override fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<T> = findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)

  override fun findByExpiryAtBefore(cutoff: Instant): List<T> = findByExpiryAtBeforeCutoff(cutoff)

  override fun deleteByUserIdAndClient(
      userId: Long,
      clientId: String,
  ) = deleteByUserIdAndClientEquals(userId, clientId)

  override fun deleteByUserIdAndClientIn(
      userId: Long,
      clientIds: Collection<String>,
  ) = deleteByUserIdAndClientIdIn(userId, clientIds)

  override fun deleteByUserId(userId: Long) = deleteByUserIdJpa(userId)

  // ========== Abstract methods (users must implement) ==========

  /**
   * Find all tokens for a user, ordered by updated_at DESC.
   *
   * JPA example: `jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)`
   */
  protected abstract fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>

  /**
   * Find token for a specific user and client.
   *
   * JPA example: `jpaRepository.findByUserIdAndClient(userId, client).orElse(null)`
   */
  protected abstract fun findByUserIdAndClientEquals(
      userId: Long,
      client: String,
  ): T?

  /**
   * Find tokens for a user with a specific subtype, ordered by updated_at DESC.
   *
   * JPA example: `jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)`
   */
  protected abstract fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      subtype: String,
  ): List<T>

  /**
   * Find all tokens that expired before the cutoff.
   *
   * JPA example: `jpaRepository.findByExpiryAtBefore(cutoff)`
   */
  protected abstract fun findByExpiryAtBeforeCutoff(cutoff: Instant): List<T>

  /**
   * Delete token for a specific user and client.
   *
   * JPA example: `jpaRepository.deleteByUserIdAndClient(userId, client)` with @Modifying @Query
   */
  protected abstract fun deleteByUserIdAndClientEquals(
      userId: Long,
      client: String,
  )

  /**
   * Delete tokens for a user with clients in the given collection.
   *
   * JPA example: `jpaRepository.deleteByUserIdAndClientIn(userId, clients)` with @Modifying @Query
   */
  protected abstract fun deleteByUserIdAndClientIdIn(
      userId: Long,
      clientIds: Collection<String>,
  )

  /**
   * Delete all tokens for a user.
   *
   * JPA example: `jpaRepository.deleteByUserId(userId)` with @Modifying @Query
   */
  protected abstract fun deleteByUserIdJpa(userId: Long)
}
