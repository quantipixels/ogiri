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
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

/**
 * Adapter that implements OgiriTokenRepository by delegating to SampleTokenJpaRepository.
 *
 * This adapter extends AbstractJpaTokenRepositoryAdapter which provides standard implementations.
 * Only the custom query delegations need to be implemented.
 */
@Repository
@Primary
class SampleTokenRepositoryAdapter(
    jpaRepository: SampleTokenJpaRepository,
) : AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenJpaRepository>(jpaRepository) {

  override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken> =
      getJpaRepository().findByUserIdOrderByUpdatedAtDesc(userId)

  override fun findByUserIdAndClientEquals(
      userId: Long,
      client: String,
  ): SampleToken? = getJpaRepository().findByUserIdAndClient(userId, client).orElse(null)

  override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      subtype: String,
  ): List<SampleToken> =
      getJpaRepository().findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)

  override fun findByExpiryAtBeforeCutoff(cutoff: Instant): List<SampleToken> =
      getJpaRepository().findByExpiryAtBefore(cutoff)

  override fun deleteByUserIdAndClientEquals(
      userId: Long,
      client: String,
  ) = getJpaRepository().deleteByUserIdAndClient(userId, client)

  override fun deleteByUserIdAndClientIdIn(
      userId: Long,
      clientIds: Collection<String>,
  ) = getJpaRepository().deleteByUserIdAndClientIn(userId, clientIds)

  override fun deleteByUserIdJpa(userId: Long) = getJpaRepository().deleteByUserId(userId)
}
