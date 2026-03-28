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

import com.quantipixels.ogiri.security.spi.OgiriUser
import java.time.Instant
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** Common test fixtures and builders for test data. */
object TestFixtures {
  /**
   * Create a test OgiriUser configured for use in unit tests.
   *
   * @param userId The user's id to return from getOgiriUserId(); defaults to 1.
   * @param username The username to return from getUsername(); defaults to "testuser".
   * @return An OgiriUser with the given id and username, password `"password"`, a single
   *   `ROLE_USER` authority, and all account-status flags set to `true`.
   */
  fun testUser(
      userId: Long = 1L,
      username: String = "testuser",
  ): OgiriUser =
      object : OgiriUser {
        override fun getOgiriUserId(): Long = userId

        override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
            mutableListOf(SimpleGrantedAuthority("ROLE_USER"))

        override fun getPassword(): String = "password"

        override fun getUsername(): String = username

        override fun isAccountNonExpired(): Boolean = true

        override fun isAccountNonLocked(): Boolean = true

        override fun isCredentialsNonExpired(): Boolean = true

        override fun isEnabled(): Boolean = true
      }

  /** Returns a client identifier in the form `"<name>-client"`. */
  fun testClientId(name: String = "test"): String = "$name-client"

  /** An [Instant] that is [seconds] seconds after the current time. */
  fun futureExpiry(seconds: Long = 3600): Instant = Instant.now().plusSeconds(seconds)

  /** An [Instant] that is [seconds] seconds before the current time. */
  fun pastExpiry(seconds: Long = 3600): Instant = Instant.now().minusSeconds(seconds)

  /** Hardcoded JWT-like bearer token for use in authentication-related tests. */
  fun testBearerToken(): String =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP7THsR8U"

  /** Reproducible bcrypt-like hashed token string for test fixtures. */
  fun testHashedToken(): String = "\$2a\$10\$abcdefghijklmnopqrstuvwxyz1234567890123456789012345"

  /** Builder for creating test tokens fluently. */
  class TokenBuilder(
      var id: Long = 0,
      var userId: Long = 1L,
      var client: String = "test-client",
      var token: String = testHashedToken(),
      var tokenType: String = "app",
      var expiryAt: Instant = futureExpiry(),
      var createdAt: Instant = Instant.now(),
      var updatedAt: Instant = Instant.now(),
      var tokenUpdatedAt: Instant = Instant.now(),
      var tokenSubtype: String? = null,
      var lastToken: String? = null,
      var previousToken: String? = null,
      var lastUsedAt: Instant? = null,
  ) {
    fun withId(id: Long) = apply { this.id = id }

    fun withUserId(userId: Long) = apply { this.userId = userId }

    fun withClient(client: String) = apply { this.client = client }

    fun withToken(token: String) = apply { this.token = token }

    fun withOgiriTokenType(tokenType: String) = apply { this.tokenType = tokenType }

    fun withExpiry(expiryAt: Instant) = apply { this.expiryAt = expiryAt }

    /** Marks the token as expired by setting its expiry to a past instant. */
    fun withExpired() = apply { this.expiryAt = pastExpiry() }

    fun withTokenSubtype(tokenSubtype: String?) = apply { this.tokenSubtype = tokenSubtype }

    fun withLastToken(lastToken: String?) = apply { this.lastToken = lastToken }

    fun withPreviousToken(previousToken: String?) = apply { this.previousToken = previousToken }

    fun withLastUsedAt(lastUsedAt: Instant?) = apply { this.lastUsedAt = lastUsedAt }

    fun build(): TestToken =
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

  /**
   * Creates a TokenBuilder preconfigured with default test values.
   *
   * @return A new TokenBuilder instance for constructing test tokens.
   */
  fun token(): TokenBuilder = TokenBuilder()
}
