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
    /** Primary key - mutable for testing. */
    override var id: Long = 0,
    /** User ID. */
    override val userId: Long,
    /** Client identifier. */
    override val client: String,
    /** Token hash. */
    override var token: String,
    /** Token type ("app" or "sub"). */
    override val tokenType: String = "app",
    /** Expiration time. */
    override var expiryAt: Instant,
    /** Creation time. */
    override val createdAt: Instant = Instant.now(),
    /** Last update time - mutable for testing. */
    override var updatedAt: Instant = Instant.now(),
    /** Last token update time - mutable for testing. */
    override var tokenUpdatedAt: Instant = Instant.now(),
    /** Sub-token type. */
    override var tokenSubtype: String? = null,
    /** Previous token hash - mutable for testing. */
    override var lastToken: String? = null,
    /** Token before last - mutable for testing. */
    override var previousToken: String? = null,
    /** Last used timestamp - mutable for testing. */
    override var lastUsedAt: Instant? = null,
) : OgiriBaseToken() {
  /**
   * Plain (unhashed) token value - temporary for testing. Note: This uses the inherited var
   * plainToken from BaseToken.
   */
  companion object {
    /**
     * Create a TestToken populated with the minimal fields commonly needed in tests.
     *
     * @param userId The ID of the token owner.
     * @param client The client identifier associated with the token.
     * @param token The stored token value (e.g., a hashed token).
     * @param expiryAt The instant when the token expires.
     * @return A TestToken instance with the provided values and defaults for remaining fields.
     */
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

    /**
     * Constructs a TestToken with an expiry time already in the past.
     *
     * @param userId The user ID to assign to the token.
     * @param client The client identifier to assign to the token.
     * @return A TestToken whose `expiryAt` is set to one second before the current instant.
     */
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
