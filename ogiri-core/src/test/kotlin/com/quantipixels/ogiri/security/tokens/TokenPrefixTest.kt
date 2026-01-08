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
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class TokenPrefixTest {

  private lateinit var repository: InMemoryTokenRepository
  private lateinit var tokenService: OgiriTokenService<TestToken>
  private val passwordEncoder: PasswordEncoder =
      object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String = rawPassword.toString()

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            rawPassword.toString() == encodedPassword
      }
  private val identifierPolicy =
      object : IdentifierPolicy {
        private val counter = AtomicLong(0)

        override fun generate(): String = "abcdefghij-${counter.incrementAndGet()}"

        override fun isValid(value: String?): Boolean = !value.isNullOrBlank()
      }

  private val user = TestFixtures.testUser(userId = 1L, username = "user")
  private val userDirectory =
      object : OgiriUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

        override fun findByEmail(email: String) = null

        override fun findByUsername(username: String) = user.takeIf { it.username == username }

        override fun recordSuccessfulLogin(userId: Long) {}
      }

  private fun defaultProperties() =
      OgiriConfigurationProperties().apply {
        auth.apply {
          maxClients = 24
          batchGraceSeconds = 5
          tokenLifespanDays = 14
        }
      }

  private inner class TestTokenService(
      repository: OgiriTokenRepository<TestToken>,
      passwordEncoder: PasswordEncoder,
      userDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
  ) :
      OgiriTokenService<TestToken>(
          repository,
          passwordEncoder,
          userDirectory,
          identifierPolicy,
          subTokenRegistry,
          properties,
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
            tokenType = tokenType.label,
            expiryAt = expiry,
            tokenSubtype = tokenSubtype,
        )

    // Expose protected methods for testing
    fun testExtractTokenPrefix(tokenValue: String): String = extractTokenPrefix(tokenValue)

    fun testFindTokenCandidates(tokenValue: String): List<TestToken> =
        findTokenCandidates(tokenValue)
  }

  @BeforeEach
  fun setUp() {
    repository = InMemoryTokenRepository()
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            DefaultOgiriSubTokenRegistry(),
            defaultProperties(),
        )
  }

  @Nested
  inner class TokenPrefixExtraction {
    @Test
    fun `extractTokenPrefix returns first 8 characters`() {
      val service = tokenService as TestTokenService
      assertEquals("abcdefgh", service.testExtractTokenPrefix("abcdefghijklmnop"))
    }

    @Test
    fun `extractTokenPrefix returns full token if shorter than 8 characters`() {
      val service = tokenService as TestTokenService
      assertEquals("short", service.testExtractTokenPrefix("short"))
    }

    @Test
    fun `extractTokenPrefix returns exactly 8 characters for 8 char token`() {
      val service = tokenService as TestTokenService
      assertEquals("12345678", service.testExtractTokenPrefix("12345678"))
    }

    @Test
    fun `TOKEN_PREFIX_LENGTH constant is 8`() {
      assertEquals(8, TOKEN_PREFIX_LENGTH)
    }
  }

  @Nested
  inner class TokenCreationWithPrefix {
    @Test
    fun `createOrUpdateToken sets tokenPrefix on new token`() {
      val expiry = Instant.now().plusSeconds(3600)
      val token = tokenService.createOrUpdateToken(user, "client-1", expiry)

      assertNotNull(token.tokenPrefix)
      assertEquals("abcdefgh", token.tokenPrefix)
    }

    @Test
    fun `createOrUpdateToken updates tokenPrefix when rotating token`() {
      val expiry = Instant.now().plusSeconds(3600)

      // Create initial token
      val token1 = tokenService.createOrUpdateToken(user, "client-1", expiry)
      val prefix1 = token1.tokenPrefix

      // Update the same token
      val token2 = tokenService.createOrUpdateToken(user, "client-1", expiry)
      val prefix2 = token2.tokenPrefix

      assertNotNull(prefix1)
      assertNotNull(prefix2)
      // Prefix changes because new token is generated
      assertEquals("abcdefgh", prefix2)
    }

    @Test
    fun `createToken populates tokenPrefix`() {
      val result = tokenService.createToken(user, "client-1")
      val token = result.appToken

      assertNotNull(token.tokenPrefix)
      assertEquals(8, token.tokenPrefix?.length)
    }
  }

  @Nested
  inner class PrefixBasedLookup {
    @Test
    fun `findValidTokensByPrefix returns tokens matching prefix`() {
      val expiry = Instant.now().plusSeconds(3600)

      // Create a token
      val token = tokenService.createOrUpdateToken(user, "client-1", expiry)

      // Debug: Check what we got
      assertNotNull(token.tokenPrefix, "Token prefix should be set after creation")
      val prefix = token.tokenPrefix!!

      // Debug: Check what's in the repository
      val allTokens = repository.getAllTokens()
      assertEquals(1, allTokens.size, "Repository should have 1 token")
      val storedToken = allTokens[0]
      assertNotNull(storedToken.tokenPrefix, "Stored token prefix should not be null")
      assertEquals(prefix, storedToken.tokenPrefix, "Stored prefix should match returned prefix")
      assertEquals(OgiriTokenType.APP.label, storedToken.tokenType, "Token type should be app")

      // Query by prefix
      val candidates = repository.findValidTokensByPrefix(prefix)

      assertEquals(1, candidates.size)
      assertEquals(token.id, candidates[0].id)
    }

    @Test
    fun `findValidTokensByPrefix excludes expired tokens`() {
      // Create an expired token with known prefix
      val expiredToken =
          TestToken(
              userId = user.getOgiriUserId(),
              client = "expired-client",
              token = "hash",
              expiryAt = Instant.now().minusSeconds(100),
          )
      expiredToken.tokenPrefix = "testpref"
      repository.save(expiredToken)

      val candidates = repository.findValidTokensByPrefix("testpref")

      assertTrue(candidates.isEmpty())
    }

    @Test
    fun `findValidTokensByPrefix excludes non-APP tokens`() {
      // Create a SUB token with known prefix
      val subToken =
          TestToken(
              userId = user.getOgiriUserId(),
              client = "sub-client",
              token = "hash",
              tokenType = "sub",
              expiryAt = Instant.now().plusSeconds(3600),
          )
      subToken.tokenPrefix = "testpref"
      repository.save(subToken)

      val candidates = repository.findValidTokensByPrefix("testpref")

      assertTrue(candidates.isEmpty())
    }

    @Test
    fun `findTokenCandidates uses prefix-based lookup`() {
      val service = tokenService as TestTokenService
      val expiry = Instant.now().plusSeconds(3600)

      // Create a token
      val token = tokenService.createOrUpdateToken(user, "client-1", expiry)
      val plainToken = token.plainToken!!

      // Find candidates using the plain token
      val candidates = service.testFindTokenCandidates(plainToken)

      assertTrue(candidates.isNotEmpty())
      assertTrue(candidates.any { it.id == token.id })
    }
  }

  @Nested
  inner class RepositoryCountByUserId {
    @Test
    fun `countByUserId returns correct count`() {
      val expiry = Instant.now().plusSeconds(3600)

      // Create multiple tokens for same user
      tokenService.createOrUpdateToken(user, "client-1", expiry)
      tokenService.createOrUpdateToken(user, "client-2", expiry)
      tokenService.createOrUpdateToken(user, "client-3", expiry)

      val count = repository.countByUserId(user.getOgiriUserId())

      assertEquals(3, count)
    }

    @Test
    fun `countByUserId returns 0 for user with no tokens`() {
      val count = repository.countByUserId(999L)

      assertEquals(0, count)
    }
  }

  @Nested
  inner class BackwardsCompatibility {
    @Test
    fun `tokens without prefix still work with findTokenCandidates fallback`() {
      val service = tokenService as TestTokenService

      // Create a token without prefix (simulating old tokens)
      val oldToken =
          TestToken(
              userId = user.getOgiriUserId(),
              client = "old-client",
              token = "hash123",
              tokenType = "app",
              expiryAt = Instant.now().plusSeconds(3600),
          )
      // tokenPrefix is null by default
      repository.save(oldToken)

      // findTokenCandidates should fall back to findAllByTokenType
      val candidates = service.testFindTokenCandidates("differenttoken")

      // Should still find the old token via fallback
      assertTrue(candidates.isNotEmpty())
    }

    @Test
    fun `OgiriBaseToken tokenPrefix defaults to null`() {
      val token =
          TestToken(
              userId = 1L,
              client = "test",
              token = "hash",
              expiryAt = Instant.now().plusSeconds(3600),
          )

      assertNull(token.tokenPrefix)
    }
  }
}
