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
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Sample JPA implementation of TokenRepository.
 *
 * This demonstrates how to implement TokenRepository using Spring Data JPA. It extends both
 * JpaRepository (for JPA persistence) and TokenRepository (for the ogiri abstraction), providing
 * implementations for all required methods.
 *
 * The default JPA query methods derive queries from method names:
 * - findByUserIdAndClientEquals -> SELECT where user_id = ? AND client_id = ?
 * - findByExpiryAtBefore -> SELECT where expiry_at < ?
 * - deleteByUserIdAndClientEquals -> DELETE where user_id = ? AND client_id = ?
 * - deleteByExpiryAtBefore -> DELETE where expiry_at < ?
 */
@Repository
interface SampleTokenRepository :
    JpaRepository<SampleToken, Long>, OgiriTokenRepository<SampleToken> {
  override fun findAllByUserId(userId: Long): List<SampleToken> =
      findByUserIdOrderByUpdatedAtDesc(userId)

  override fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): SampleToken? = findByUserIdAndClientJpa(userId, clientId).orElse(null)

  override fun findByExpiryAtBefore(cutoff: Instant): List<SampleToken> =
      findByExpiryAtBeforeCutoff(cutoff)

  override fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<SampleToken> = findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)

  override fun deleteByUserIdAndClient(
      userId: Long,
      clientId: String,
  ) = deleteByUserIdAndClientJpa(userId, clientId)

  override fun deleteByUserIdAndClientIn(
      userId: Long,
      clientIds: Collection<String>,
  ) = deleteByUserIdAndClientJpaIn(userId, clientIds)

  override fun deleteByUserId(userId: Long) = deleteByUserIdJpa(userId)

  override fun deleteByExpiryAtBefore(cutoff: Instant): Int = deleteByExpiryAtBeforeCutoff(cutoff)

  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken>

  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun findByUserIdAndClientJpa(
      userId: Long,
      client: String,
  ): java.util.Optional<SampleToken>

  @Query("SELECT t FROM SampleToken t WHERE t.expiryAt < ?1")
  fun findByExpiryAtBeforeCutoff(expiryAt: Instant): List<SampleToken>

  @Query(
      "SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<SampleToken>

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun deleteByUserIdAndClientJpa(
      userId: Long,
      client: String,
  )

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  fun deleteByUserIdAndClientJpaIn(
      userId: Long,
      clients: Collection<String>,
  )

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  fun deleteByUserIdJpa(userId: Long)

  /** Delete token. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t = ?1")
  override fun delete(token: SampleToken)

  /** Delete all tokens that expired before a specific cutoff time. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.expiryAt < ?1")
  fun deleteByExpiryAtBeforeCutoff(expiryAt: Instant): Int
}
