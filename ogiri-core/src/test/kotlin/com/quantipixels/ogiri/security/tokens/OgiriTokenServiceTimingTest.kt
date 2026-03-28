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

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.core.SecurityServiceException
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Regression guard for timing normalisation in verifyUser.
 *
 * DUMMY_HASH must be a well-formed BCrypt hash so that BCryptPasswordEncoder.matches() performs the
 * full key-derivation rounds on the "user not found" path, preventing user enumeration via
 * response-time diff.
 */
class OgiriTokenServiceTimingTest {

  @Test
  fun `verifyUser with unknown user throws SecurityServiceException not IllegalArgumentException`() {
    val service = buildService()

    assertThrows(SecurityServiceException::class.java) {
      service.verifyUser(
          MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" },
          MockHttpServletResponse(),
          "unknown@example.com",
          "any-password",
      )
    }
  }

  private fun buildService(): OgiriTokenService<TestToken> {
    val user = TestFixtures.testUser(userId = 1L, username = "testuser")
    val counter = AtomicLong(0)
    val props =
        OgiriConfigurationProperties().apply {
          auth.apply {
            maxClients = 24
            batchGraceSeconds = 5
            tokenLifespanDays = 14
          }
        }
    return object :
        OgiriTokenService<TestToken>(
            InMemoryTokenRepository(),
            BCryptPasswordEncoder(),
            object : OgiriUserDirectory {
              override fun loadUserByUsername(username: String) = user

              override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

              override fun findByEmail(email: String) = user.takeIf { "known@example.com" == email }

              override fun findByUsername(username: String) =
                  user.takeIf { it.username == username }

              override fun recordSuccessfulLogin(userId: Long) {}
            },
            object : IdentifierPolicy {
              override fun generate() = "tok-${counter.incrementAndGet()}"

              override fun isValid(value: String?) = !value.isNullOrBlank()
            },
            DefaultOgiriSubTokenRegistry(emptyList()),
            props,
        ) {
      override fun tokenFactory(
          userId: Long,
          client: String,
          hashedToken: String,
          tokenType: OgiriTokenType,
          expiry: Instant,
          tokenSubtype: String?,
          plainTokenValue: String,
      ) =
          TestToken(
              userId = userId,
              client = client,
              token = hashedToken,
              tokenType = tokenType.name,
              expiryAt = expiry,
              tokenSubtype = tokenSubtype,
          )
    }
  }
}
