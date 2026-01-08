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

import com.quantipixels.ogiri.jpa.AbstractJpaTokenRepositoryAdapter
import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import java.time.Instant
import org.springframework.stereotype.Repository

/**
 * Sample token repository adapter using ogiri-jpa module.
 *
 * This demonstrates the simplified approach using AbstractJpaTokenRepositoryAdapter from ogiri-jpa.
 * The adapter reduces boilerplate by providing standard CRUD implementations and requiring only
 * delegation to custom JPA query methods.
 *
 * Implementation steps:
 * 1. Extend AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenJpaRepository>
 * 2. Implement abstract methods by delegating to the JPA repository
 *
 * Benefits:
 * - Reduces implementation from ~100 lines to ~20 lines
 * - Type-safe delegation to JPA repository methods
 * - Maintains clear separation between OgiriTokenRepository contract and JPA implementation
 * - Standard CRUD operations (save, findById, delete) provided by base class
 *
 * Before (manual implementation): ~100 lines with all method implementations After (using adapter):
 * ~20 lines with just delegation methods
 */
@Repository
class SampleTokenRepository(jpa: SampleTokenJpaRepository) :
    AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenJpaRepository>(jpa) {

  override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
      jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)

  override fun findByUserIdAndClientEquals(userId: Long, client: String) =
      jpaRepository.findByUserIdAndClient(userId, client)

  override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String) =
      jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)

  override fun findByExpiryAtBeforeCutoff(cutoff: Instant) =
      jpaRepository.findByExpiryAtBefore(cutoff)

  override fun deleteByUserIdAndClientEquals(userId: Long, client: String) =
      jpaRepository.deleteByUserIdAndClient(userId, client)

  override fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>) =
      jpaRepository.deleteByUserIdAndClientIn(userId, clientIds)

  override fun deleteByUserIdJpa(userId: Long) = jpaRepository.deleteByUserId(userId)
}
