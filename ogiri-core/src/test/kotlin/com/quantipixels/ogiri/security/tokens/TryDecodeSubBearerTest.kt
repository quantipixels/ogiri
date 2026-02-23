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
import com.quantipixels.ogiri.security.spi.OgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import com.quantipixels.ogiri.security.testutil.emptyObjectProvider
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class TryDecodeSubBearerTest {
  private lateinit var tokenService: OgiriTokenService<TestToken>

  private val passwordEncoder =
      object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String = rawPassword.toString()

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            rawPassword.toString() == encodedPassword
      }

  private val identifierPolicy =
      object : IdentifierPolicy {
        private val counter = AtomicLong(0)

        override fun generate(): String = "tok-${counter.incrementAndGet()}"

        override fun isValid(value: String?): Boolean = !value.isNullOrBlank()
      }

  private val user = TestFixtures.testUser()
  private val userDirectory =
      object : OgiriUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

        override fun findByEmail(email: String) = null

        override fun findByUsername(username: String) = user.takeIf { it.username == username }

        override fun recordSuccessfulLogin(userId: Long) {}
      }

  @BeforeEach
  fun setup() {
    val repository = InMemoryTokenRepository()
    tokenService =
        object :
            OgiriTokenService<TestToken>(
                repository,
                passwordEncoder,
                userDirectory,
                identifierPolicy,
                DefaultOgiriSubTokenRegistry(emptyList()),
                OgiriConfigurationProperties(),
                emptyObjectProvider<OgiriAuditHook>(),
                emptyObjectProvider<OgiriRateLimitHook>(),
                emptyObjectProvider(),
            ) {
          override fun tokenFactory(
              userId: Long,
              client: String,
              hashedToken: String,
              tokenType: OgiriTokenType,
              expiry: Instant,
              tokenSubtype: String?,
              plainTokenValue: String,
          ): TestToken =
              TestToken(
                      userId = userId,
                      client = client,
                      token = hashedToken,
                      tokenType = tokenType.name,
                      expiryAt = expiry,
                      tokenSubtype = tokenSubtype,
                  )
                  .apply { plainToken = plainTokenValue }
        }
  }

  private fun encode(json: String): String =
      Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

  @Test
  fun `valid JSON with all fields returns SubTokenHeader`() {
    val encoded = encode("""{"client":"c1","token":"t1","expiry":"2026-01-01T00:00:00Z"}""")
    val result = tokenService.tryDecodeSubBearer(encoded)
    assertNotNull(result)
    assertEquals("c1", result!!.client)
    assertEquals("t1", result.token)
    assertEquals("2026-01-01T00:00:00Z", result.expiry)
  }

  @Test
  fun `valid JSON with missing fields returns SubTokenHeader with nulls`() {
    val encoded = encode("""{"other":"value"}""")
    val result = tokenService.tryDecodeSubBearer(encoded)
    assertNotNull(result)
    assertNull(result!!.client)
    assertNull(result.token)
    assertNull(result.expiry)
  }

  @Test
  fun `malformed Base64 returns null`() {
    val result = tokenService.tryDecodeSubBearer("not-valid-base64!!!")
    assertNull(result)
  }

  @Test
  fun `valid Base64 but invalid JSON returns null`() {
    val encoded = encode("this is not json")
    val result = tokenService.tryDecodeSubBearer(encoded)
    assertNull(result)
  }

  @Test
  fun `valid Base64 JSON array (wrong shape) returns null`() {
    val encoded = encode("""[1, 2, 3]""")
    val result = tokenService.tryDecodeSubBearer(encoded)
    assertNull(result)
  }

  @Test
  fun `empty string returns null`() {
    val result = tokenService.tryDecodeSubBearer("")
    assertNull(result)
  }

  @Test
  fun `valid JSON with numeric field values returns SubTokenHeader with nulls for those fields`() {
    val encoded = encode("""{"client":123,"token":true,"expiry":null}""")
    val result = tokenService.tryDecodeSubBearer(encoded)
    assertNotNull(result)
    assertNull(result!!.client)
    assertNull(result.token)
    assertNull(result.expiry)
  }
}
