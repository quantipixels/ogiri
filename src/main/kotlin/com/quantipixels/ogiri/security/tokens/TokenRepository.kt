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
package com.quantipixels.ogiri.security.tokens

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.stereotype.Repository
import java.time.Instant

@NoRepositoryBean
interface TokenRepository<T : BaseToken> : JpaRepository<T, Long> {
  fun findAllByUserId(userId: Long): List<T>

  fun findByUserIdAndClient(
    userId: Long,
    clientId: String,
  ): T?

  fun deleteByUserIdAndClient(
    userId: Long,
    clientId: String,
  )

  fun deleteByUserIdAndClientIn(
    userId: Long,
    clientIds: Collection<String>,
  )

  fun deleteByUserId(userId: Long)

  fun findByExpiryAtBefore(cutoff: Instant): List<T>
}

@Repository
interface OgiriTokenRepository : TokenRepository<Token>
