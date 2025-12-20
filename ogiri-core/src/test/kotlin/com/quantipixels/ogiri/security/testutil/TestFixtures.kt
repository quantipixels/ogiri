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
       * @return An OgiriUser with the given id and username, password `"password"`, a single `ROLE_USER` authority, and all account-status flags set to `true`.
       */
  fun testUser(
      userId: Long = 1L,
      username: String = "testuser",
  ): OgiriUser =
      object : OgiriUser {
        /**
 * Retrieve the user's Ogiri ID.
 *
 * @return The user's Ogiri ID.
 */
override fun getOgiriUserId(): Long = userId

        /**
             * Provide the user's authorities as a single-user role.
             *
             * @return A mutable collection containing one `GrantedAuthority` with role `ROLE_USER`.
             */
            override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
            mutableListOf(SimpleGrantedAuthority("ROLE_USER"))

        /**
 * Returns the fixed password used for the test user.
 *
 * @return The password string "password".
 */
override fun getPassword(): String = "password"

        /**
 * Retrieves the user's username.
 *
 * @return The username.
 */
override fun getUsername(): String = username

        /**
 * Indicates whether the account is not expired.
 *
 * @return `true` if the account is not expired, `false` otherwise. This implementation always returns `true` for the test user fixture.
 */
override fun isAccountNonExpired(): Boolean = true

        /**
 * Indicates whether the user's account is not locked.
 *
 * @return `true` if the account is not locked, `false` otherwise.
 */
override fun isAccountNonLocked(): Boolean = true

        /**
 * Indicates whether the user's credentials have not expired.
 *
 * @return `true` if the user's credentials are not expired, `false` otherwise.
 */
override fun isCredentialsNonExpired(): Boolean = true

        /**
 * Indicates whether the user account is enabled.
 *
 * @return `true` if the account is enabled, `false` otherwise.
 */
override fun isEnabled(): Boolean = true
      }

  /**
 * Builds a test client identifier from a base name.
 *
 * @param name Base name to use for the client identifier.
 * @return The client identifier in the form "<name>-client".
 */
  fun testClientId(name: String = "test"): String = "$name-client"

  /**
 * Creates an Instant that is the given number of seconds after the current time.
 *
 * @param seconds Number of seconds from now to add to the current time.
 * @return An Instant representing the current time plus `seconds`.
 */
  fun futureExpiry(seconds: Long = 3600): Instant = Instant.now().plusSeconds(seconds)

  /**
 * Produce an Instant representing a point in time before now.
 *
 * @param seconds Number of seconds before the current time to produce.
 * @return An Instant that is `seconds` seconds earlier than the current time.
 */
  fun pastExpiry(seconds: Long = 3600): Instant = Instant.now().minusSeconds(seconds)

  /**
       * Fixed JWT-like bearer token used in tests.
       *
       * The token is a hardcoded JWT-like string intended for use in authentication-related tests.
       *
       * @return A hardcoded JWT-like bearer token string.
       */
  fun testBearerToken(): String =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP7THsR8U"

  /**
 * Reproducible bcrypt-like hashed token string used in tests.
 *
 * @return The constant hashed token string for test fixtures.
 */
  fun testHashedToken(): String = "\$2a\$10\$abcdefghijklmnopqrstuvwxyz1234567890123456789012345"

  /** Builder for creating test tokens fluently. */
  class TokenBuilder(
      var id: Long = 0,
      var userId: Long = 1L,
      var client: String = "test-client",
      var token: String = testHashedToken(),
      var tokenType: String = "APP",
      var expiryAt: Instant = futureExpiry(),
      var createdAt: Instant = Instant.now(),
      var updatedAt: Instant = Instant.now(),
      var tokenUpdatedAt: Instant = Instant.now(),
      var tokenSubtype: String? = null,
      var lastToken: String? = null,
      var previousToken: String? = null,
      var lastUsedAt: Instant? = null,
  ) {
    /**
 * Sets the token id for the token being built.
 *
 * @param id The id to assign to the token.
 * @return This TokenBuilder instance.
 */
fun withId(id: Long) = apply { this.id = id }

    /**
 * Set the token's owner user ID on the builder.
 *
 * @param userId The ID of the user who owns the token.
 * @return This builder instance.
 */
fun withUserId(userId: Long) = apply { this.userId = userId }

    /**
 * Sets the client identifier for the token being built.
 *
 * @param client The client identifier to assign to the token.
 * @return This builder instance for method chaining.
 */
fun withClient(client: String) = apply { this.client = client }

    /**
 * Set the token string to include in the built TestToken.
 *
 * @param token The token value to set (typically a hashed token string).
 * @return This builder instance for method chaining.
 */
fun withToken(token: String) = apply { this.token = token }

    /**
 * Set the Ogiri token type for the builder.
 *
 * @param tokenType Token type identifier (for example, `"APP"`).
 * @return This TokenBuilder instance for chaining.
 */
fun withOgiriTokenType(tokenType: String) = apply { this.tokenType = tokenType }

    /**
 * Set the token expiry instant for the builder.
 *
 * @param expiryAt The instant when the token should expire.
 * @return This builder instance for method chaining.
 */
fun withExpiry(expiryAt: Instant) = apply { this.expiryAt = expiryAt }

    /**
 * Marks the token as expired by setting its expiry to a past instant.
 *
 * @return this TokenBuilder with `expiryAt` set to a past `Instant`.
 */
fun withExpired() = apply { this.expiryAt = pastExpiry() }

    /**
 * Set the token subtype on this builder.
 *
 * @param tokenSubtype The token subtype to assign, or `null` to clear it.
 * @return This builder instance with the updated `tokenSubtype`.
 */
fun withTokenSubtype(tokenSubtype: String?) = apply { this.tokenSubtype = tokenSubtype }

    /**
 * Sets the builder's last token value.
 *
 * @param lastToken The last token string to record, or `null` to clear it.
 * @return This TokenBuilder instance.
 */
fun withLastToken(lastToken: String?) = apply { this.lastToken = lastToken }

    /**
 * Sets the previous token value for the token being built.
 *
 * @param previousToken The previous token string to assign, or `null` to unset it.
 * @return This builder instance.
 */
fun withPreviousToken(previousToken: String?) = apply { this.previousToken = previousToken }

    /**
 * Sets the timestamp when the token was last used.
 *
 * @param lastUsedAt The Instant when the token was last used, or `null` if it has never been used.
 * @return This builder instance.
 */
fun withLastUsedAt(lastUsedAt: Instant?) = apply { this.lastUsedAt = lastUsedAt }

    /**
         * Builds a TestToken populated with the builder's current property values.
         *
         * @return A TestToken instance whose fields mirror the builder's configured properties.
         */
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
