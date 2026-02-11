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
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class TokenCleanupTest {

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

        override fun generate(): String = "tok-${counter.incrementAndGet()}"

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
          emptyObjectProvider<OgiriAuditHook>(),
          emptyObjectProvider<OgiriRateLimitHook>(),
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
  }

  @BeforeEach
  fun setUp() {
    repository = InMemoryTokenRepository()
    val props = defaultProperties()
    val subTokenRegistry = DefaultOgiriSubTokenRegistry()
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            props,
        )
  }

  @Nested
  inner class DeleteByExpiryAtBeforeTests {

    @Test
    fun `deletes tokens with expiry before cutoff`() {
      val now = Instant.now()
      val expiredToken1 =
          TestToken.create(userId = 1L, client = "c1", expiryAt = now.minusSeconds(100))
      val expiredToken2 =
          TestToken.create(userId = 1L, client = "c2", expiryAt = now.minusSeconds(50))
      val validToken = TestToken.create(userId = 1L, client = "c3", expiryAt = now.plusSeconds(100))

      repository.save(expiredToken1)
      repository.save(expiredToken2)
      repository.save(validToken)

      val deletedCount = repository.deleteByExpiryAtBefore(now)

      assertEquals(2, deletedCount)
      assertEquals(1, repository.getCount())
      assertEquals("c3", repository.getAllTokens().first().client)
    }

    @Test
    fun `returns zero when no tokens expired`() {
      val now = Instant.now()
      val validToken1 =
          TestToken.create(userId = 1L, client = "c1", expiryAt = now.plusSeconds(100))
      val validToken2 =
          TestToken.create(userId = 1L, client = "c2", expiryAt = now.plusSeconds(200))

      repository.save(validToken1)
      repository.save(validToken2)

      val deletedCount = repository.deleteByExpiryAtBefore(now)

      assertEquals(0, deletedCount)
      assertEquals(2, repository.getCount())
    }

    @Test
    fun `does not delete tokens with expiry equal to cutoff`() {
      val cutoff = Instant.now()
      val tokenAtCutoff = TestToken.create(userId = 1L, client = "c1", expiryAt = cutoff)
      val tokenBeforeCutoff =
          TestToken.create(userId = 1L, client = "c2", expiryAt = cutoff.minusSeconds(1))

      repository.save(tokenAtCutoff)
      repository.save(tokenBeforeCutoff)

      val deletedCount = repository.deleteByExpiryAtBefore(cutoff)

      assertEquals(1, deletedCount)
      assertEquals(1, repository.getCount())
      assertEquals("c1", repository.getAllTokens().first().client)
    }
  }

  @Nested
  inner class CleanupExpiredTokensTests {

    @Test
    fun `cleanupExpiredTokens returns count of deleted tokens`() {
      val now = Instant.now()
      val expired1 = TestToken.create(userId = 1L, client = "c1", expiryAt = now.minusSeconds(100))
      val expired2 = TestToken.create(userId = 1L, client = "c2", expiryAt = now.minusSeconds(50))
      val expired3 = TestToken.create(userId = 2L, client = "c3", expiryAt = now.minusSeconds(10))

      repository.save(expired1)
      repository.save(expired2)
      repository.save(expired3)

      val deletedCount = tokenService.cleanupExpiredTokens(now)

      assertEquals(3, deletedCount)
      assertEquals(0, repository.getCount())
    }

    @Test
    fun `cleanupExpiredTokens with no expired tokens returns zero`() {
      val now = Instant.now()
      val validToken =
          TestToken.create(userId = 1L, client = "c1", expiryAt = now.plusSeconds(3600))

      repository.save(validToken)

      val deletedCount = tokenService.cleanupExpiredTokens(now)

      assertEquals(0, deletedCount)
      assertEquals(1, repository.getCount())
    }
  }

  @Nested
  inner class TokenDeletionTests {

    @Test
    fun `deleteToken removes single token by userId and client`() {
      val token = TestToken.create(userId = 1L, client = "test-client")
      repository.save(token)

      tokenService.deleteToken(1L, "test-client")

      assertEquals(0, repository.getCount())
    }

    @Test
    fun `deleteToken with collection removes multiple tokens`() {
      val token1 = TestToken.create(userId = 1L, client = "c1")
      val token2 = TestToken.create(userId = 1L, client = "c2")
      val token3 = TestToken.create(userId = 1L, client = "c3")
      repository.save(token1)
      repository.save(token2)
      repository.save(token3)

      tokenService.deleteToken(1L, listOf("c1", "c3"))

      assertEquals(1, repository.getCount())
      assertEquals("c2", repository.getAllTokens().first().client)
    }

    @Test
    fun `deleteAllForUser removes all tokens for user`() {
      val user1Token1 = TestToken.create(userId = 1L, client = "c1")
      val user1Token2 = TestToken.create(userId = 1L, client = "c2")
      val user2Token = TestToken.create(userId = 2L, client = "c1")
      repository.save(user1Token1)
      repository.save(user1Token2)
      repository.save(user2Token)

      tokenService.deleteAllForUser(1L)

      assertEquals(1, repository.getCount())
      assertEquals(2L, repository.getAllTokens().first().userId)
    }
  }

  @Nested
  inner class GetTokenTests {

    @Test
    fun `getAllByUserId returns all tokens for user`() {
      val user1Token1 = TestToken.create(userId = 1L, client = "c1")
      val user1Token2 = TestToken.create(userId = 1L, client = "c2")
      val user2Token = TestToken.create(userId = 2L, client = "c1")
      repository.save(user1Token1)
      repository.save(user1Token2)
      repository.save(user2Token)

      val tokens = tokenService.getAllByUserId(1L)

      assertEquals(2, tokens.size)
    }

    @Test
    fun `getByUserIdAndClient returns specific token`() {
      val token = TestToken.create(userId = 1L, client = "specific-client")
      repository.save(token)

      val found = tokenService.getByUserIdAndClient(1L, "specific-client")

      assertEquals("specific-client", found?.client)
    }

    @Test
    fun `getByUserIdAndClient returns null when not found`() {
      val found = tokenService.getByUserIdAndClient(1L, "nonexistent")

      assertEquals(null, found)
    }
  }
}
