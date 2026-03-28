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
package com.quantipixels.ogiri.security.testutil

import com.quantipixels.ogiri.security.tokens.OgiriBaseToken
import java.time.Instant

/**
 * Simple in-memory Token implementation for testing.
 *
 * This data class extends BaseToken and can be used in unit tests without requiring a database or
 * JPA. It's useful for testing TokenService and TokenRepository implementations.
 *
 * All properties are mutable to allow TokenService to update them as needed.
 */
data class TestToken(
    override var id: Long = 0,
    override val userId: Long,
    override val client: String,
    override var token: String,
    override val tokenType: String = "app",
    override var expiryAt: Instant,
    override val createdAt: Instant = Instant.now(),
    override var updatedAt: Instant = Instant.now(),
    override var tokenUpdatedAt: Instant = Instant.now(),
    override var tokenSubtype: String? = null,
    override var lastToken: String? = null,
    override var previousToken: String? = null,
    override var lastUsedAt: Instant? = null,
) : OgiriBaseToken() {
  companion object {
    /** Creates a [TestToken] with the minimal fields needed in most tests. */
    fun create(
        userId: Long = 1L,
        client: String = "test-client",
        token: String = "hashed-token",
        expiryAt: Instant = Instant.now().plusSeconds(3600),
    ): TestToken =
        TestToken(
            userId = userId,
            client = client,
            token = token,
            expiryAt = expiryAt,
        )

    /** Creates a [TestToken] with `expiryAt` set to one second before now. */
    fun expired(
        userId: Long = 1L,
        client: String = "test-client",
    ): TestToken =
        TestToken(
            userId = userId,
            client = client,
            token = "hashed-token",
            expiryAt = Instant.now().minusSeconds(1),
        )

    /** Create a token with all fields populated. */
    fun full(
        id: Long = 1L,
        userId: Long = 1L,
        client: String = "test-client",
        token: String = "hashed-token",
        tokenType: String = "app",
        expiryAt: Instant = Instant.now().plusSeconds(3600),
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        tokenUpdatedAt: Instant = Instant.now(),
        tokenSubtype: String? = null,
        lastToken: String? = null,
        previousToken: String? = null,
        lastUsedAt: Instant? = null,
    ): TestToken =
        TestToken(
            id = id,
            userId = userId,
            client = client,
            token = token,
            tokenType = tokenType,
            expiryAt = expiryAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            tokenUpdatedAt = tokenUpdatedAt,
            tokenSubtype = tokenSubtype,
            lastToken = lastToken,
            previousToken = previousToken,
            lastUsedAt = lastUsedAt,
        )
  }
}
