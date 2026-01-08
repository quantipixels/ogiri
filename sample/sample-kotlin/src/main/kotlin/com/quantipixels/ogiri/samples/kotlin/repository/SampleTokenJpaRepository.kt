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
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Pure JPA repository interface for SampleToken.
 *
 * This interface contains only Spring Data JPA methods. The OgiriTokenRepository interface is
 * implemented by SampleTokenRepositoryAdapter which delegates to this repository.
 */
@Repository
interface SampleTokenJpaRepository : JpaRepository<SampleToken, Long> {

  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken>

  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1")
  fun findAllByUserId(userId: Long): List<SampleToken>

  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun findByUserIdAndClient(
      userId: Long,
      client: String,
  ): Optional<SampleToken>

  @Transactional(readOnly = true)
  @Query(
      "SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 ORDER BY t.updatedAt DESC, t.id DESC")
  fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<SampleToken>

  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.expiryAt < ?1")
  fun findByExpiryAtBefore(cutoff: Instant): List<SampleToken>

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  fun deleteByUserIdAndClient(
      userId: Long,
      client: String,
  )

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  fun deleteByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  )

  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  fun deleteByUserId(userId: Long)
}
