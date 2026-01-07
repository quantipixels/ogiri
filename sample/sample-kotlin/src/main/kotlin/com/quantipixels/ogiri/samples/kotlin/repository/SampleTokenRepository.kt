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
package com.quantipixels.ogiri.samples.kotlin.repository

import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Sample JPA repository for SampleToken persistence.
 *
 * This interface extends Spring Data JPA's JpaRepository to provide standard CRUD operations and
 * custom query methods for token management. Uses explicit @Query annotations to avoid Spring
 * Data's method name parsing.
 *
 * Note: Does NOT extend OgiriTokenRepository directly to avoid method signature conflicts. Use
 * SampleTokenRepositoryAdapter for OgiriTokenRepository implementation.
 */
@Repository
interface SampleTokenRepository : JpaRepository<SampleToken, Long> {

  /** Find all tokens for a user, ordered by most recent first. */
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken>

  /** Find a specific token by user ID and client identifier. */
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun findByUserIdAndClientEquals(
      userId: Long,
      client: String,
  ): java.util.Optional<SampleToken>

  /** Find all tokens for a user with a specific subtype. */
  @Query(
      "SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<SampleToken>

  /** Find all tokens that expired before a specific cutoff time. */
  @Query("SELECT t FROM SampleToken t WHERE t.expiryAt < ?1")
  fun findByExpiryAtBeforeCutoff(expiryAt: Instant): List<SampleToken>

  /** Delete a token by user ID and client identifier. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun deleteByUserIdAndClientEquals(
      userId: Long,
      client: String,
  )

  /** Delete tokens by user ID and multiple client identifiers. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  fun deleteByUserIdAndClientIdIn(
      userId: Long,
      clients: Collection<String>,
  )

  /** Delete all tokens for a specific user. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  fun deleteByUserIdJpa(userId: Long)

  /** Delete all tokens that expired before a specific cutoff time. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.expiryAt < ?1")
  fun deleteByExpiryAtBeforeCutoff(expiryAt: Instant): Int

  /** Count the number of tokens for a specific user. */
  @Query("SELECT COUNT(t) FROM SampleToken t WHERE t.userId = ?1")
  fun countByUserId(userId: Long): Long

  /** Find valid tokens by prefix (for optimization). */
  @Query(
      "SELECT t FROM SampleToken t WHERE t.tokenPrefix = ?1 AND t.tokenType = 'app' AND t.expiryAt > ?2")
  fun findValidTokensByPrefix(
      prefix: String,
      now: Instant,
  ): List<SampleToken>

  /** Find all tokens of a specific type. */
  @Query("SELECT t FROM SampleToken t WHERE t.tokenType = ?1")
  fun findAllByTokenType(tokenType: String): List<SampleToken>
}
