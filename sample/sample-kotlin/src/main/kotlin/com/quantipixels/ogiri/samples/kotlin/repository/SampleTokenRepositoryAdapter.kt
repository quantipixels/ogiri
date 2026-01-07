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
import org.springframework.stereotype.Repository

/**
 * Adapter that implements OgiriTokenRepository for SampleTokenRepository.
 *
 * This adapter delegates to SampleTokenRepository (JpaRepository) to provide the
 * OgiriTokenRepository interface required by ogiri's OgiriTokenService.
 *
 * The adapter pattern is used to avoid method signature conflicts between OgiriTokenRepository and
 * JpaRepository which have different generic type bounds and method signatures.
 */
@Repository
class SampleTokenRepositoryAdapter(private val jpaRepository: SampleTokenRepository) :
    OgiriTokenRepository<SampleToken> {

  override fun save(token: SampleToken): SampleToken = jpaRepository.save(token)

  override fun findById(id: Long): SampleToken? = jpaRepository.findById(id).orElse(null)

  override fun deleteById(id: Long) = jpaRepository.deleteById(id)

  override fun findAllByUserId(userId: Long): List<SampleToken> =
      jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)

  override fun findByUserIdAndClient(userId: Long, clientId: String): SampleToken? =
      jpaRepository.findByUserIdAndClientEquals(userId, clientId).orElse(null)

  override fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<SampleToken> =
      jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)

  override fun findByExpiryAtBefore(cutoff: Instant): List<SampleToken> =
      jpaRepository.findByExpiryAtBeforeCutoff(cutoff)

  override fun deleteByUserIdAndClient(userId: Long, clientId: String) =
      jpaRepository.deleteByUserIdAndClientEquals(userId, clientId)

  override fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>) =
      jpaRepository.deleteByUserIdAndClientIdIn(userId, clientIds)

  override fun deleteByUserId(userId: Long) = jpaRepository.deleteByUserIdJpa(userId)

  override fun delete(token: SampleToken) = jpaRepository.delete(token)

  override fun deleteByExpiryAtBefore(cutoff: Instant): Int =
      jpaRepository.deleteByExpiryAtBeforeCutoff(cutoff)

  override fun deleteExpiredBatch(cutoff: Instant, batchSize: Int): Int {
    // Default implementation: find expired tokens and delete in batch
    // For production, consider using native queries with LIMIT for better performance
    val expired = jpaRepository.findByExpiryAtBeforeCutoff(cutoff)
    val toDelete = expired.take(batchSize)
    jpaRepository.deleteAll(toDelete)
    return toDelete.size
  }

  override fun findValidTokensByPrefix(prefix: String, now: Instant): List<SampleToken> =
      jpaRepository.findValidTokensByPrefix(prefix, now)

  override fun findAllByTokenType(tokenType: String): List<SampleToken> =
      jpaRepository.findAllByTokenType(tokenType)

  override fun countByUserId(userId: Long): Long = jpaRepository.countByUserId(userId)
}
