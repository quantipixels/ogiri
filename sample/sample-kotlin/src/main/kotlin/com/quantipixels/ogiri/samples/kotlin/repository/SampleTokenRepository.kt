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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Repository for SampleToken using the simplified pattern.
 *
 * Since 1.3.1, OgiriTokenRepository method names follow Spring Data conventions, so Spring Data
 * automatically generates all query implementations. No adapter class needed!
 *
 * For methods that Spring Data cannot auto-generate (bulk deletes), we provide explicit @Query
 * annotations.
 */
@Repository
interface SampleTokenRepository :
    JpaRepository<SampleToken, Long>, OgiriTokenRepository<SampleToken> {

  // Spring Data auto-generates these based on method naming conventions:
  // - findByUserIdOrderByUpdatedAtDesc(userId)
  // - findByUserIdAndClient(userId, client) -> Optional<SampleToken>
  // - findByUserIdAndClientIn(userId, clients) -> List<SampleToken> (for batch sub-token loading)
  // - findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)
  // - findByExpiryAtBefore(cutoff)
  // - findByTokenType(tokenType)

  // Override countByUserId with explicit query for performance (avoids loading all tokens)
  @Query("SELECT COUNT(t) FROM SampleToken t WHERE t.userId = :userId")
  override fun countByUserId(userId: Long): Long

  // Bulk delete operations need explicit @Query (Spring Data naming convention doesn't support
  // certain operations)
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  override fun deleteByUserIdAndClient(userId: Long, client: String)

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  override fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>)

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  override fun deleteByUserId(userId: Long)
}
