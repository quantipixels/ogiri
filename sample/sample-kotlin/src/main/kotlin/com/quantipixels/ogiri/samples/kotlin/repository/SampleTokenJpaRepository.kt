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

/**
 * JPA repository interface for SampleToken with custom query methods.
 *
 * This interface defines Spring Data JPA query methods that will be used by the
 * SampleTokenRepositoryAdapter. Spring Data automatically generates implementations for these
 * methods based on their names.
 *
 * Query method naming conventions:
 * - findBy* = SELECT queries
 * - deleteBy* = DELETE queries
 * - OrderBy* = ORDER BY clause
 * - And = AND condition in WHERE clause
 *
 * These methods provide the database-specific query implementations that the adapter delegates to.
 */
interface SampleTokenJpaRepository : JpaRepository<SampleToken, Long> {

  /** Find all tokens for a user, ordered by most recently updated. */
  fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken>

  /** Find the token for a specific user and client combination. */
  fun findByUserIdAndClient(userId: Long, client: String): SampleToken?

  /** Find all tokens for a user with a specific subtype, ordered by updated time. */
  fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      subtype: String
  ): List<SampleToken>

  /** Find all tokens that have expired before a cutoff time. */
  fun findByExpiryAtBefore(cutoff: Instant): List<SampleToken>

  /** Delete the token for a specific user and client. */
  fun deleteByUserIdAndClient(userId: Long, client: String)

  /** Delete tokens for a user matching multiple client IDs. */
  fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>)

  /** Delete all tokens for a specific user. */
  fun deleteByUserId(userId: Long)
}
