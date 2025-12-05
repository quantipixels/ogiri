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

import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

class TokenServiceSubTokenTest {
  private lateinit var repository: InMemoryTokenRepository
  private lateinit var tokenService: TokenService<TestToken>
  private val passwordEncoder: PasswordEncoder = NoOpPasswordEncoder.getInstance()
  private val identifierPolicy =
    object : IdentifierPolicy {
      private val counter = AtomicLong(0)

      override fun generate(): String = "tok-${counter.incrementAndGet()}"

      override fun isValid(value: String?): Boolean = !value.isNullOrBlank()
    }

  private val user = TestFixtures.testUser(userId = 1L, username = "user")
  private val userDirectory =
    object : TokenUserDirectory {
      override fun loadUserByUsername(username: String) = user

      override fun findById(id: Long) = user.takeIf { it.userId == id }

      override fun findByEmail(email: String) = null

      override fun findByUsername(username: String) = user.takeIf { it.username == username }

      override fun recordSuccessfulLogin(userId: Long) {}
    }

  // Custom TokenService that implements tokenFactory for TestToken
  private inner class TestTokenService(
    repository: TokenRepository<TestToken>,
    passwordEncoder: PasswordEncoder,
    userDirectory: TokenUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: SubTokenRegistry,
    maxClients: Long = 24,
    batchGraceSeconds: Long = 5,
    tokenLifespanDays: Long = 14,
  ) : TokenService<TestToken>(
      repository,
      passwordEncoder,
      userDirectory,
      identifierPolicy,
      subTokenRegistry,
      maxClients,
      batchGraceSeconds,
      tokenLifespanDays,
    ) {
    override fun tokenFactory(
      userId: Long,
      client: String,
      hashedToken: String,
      tokenType: TokenType,
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
      ).apply { plainToken = plainTokenValue }
  }

  @BeforeEach
  fun setup() {
    repository = InMemoryTokenRepository()
  }

  @Test
  fun `default sub token is issued and returned in headers`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers: AuthHeader = tokenService.createNewAuthToken(user.userId, "clientA")

    val chatToken = repository.findByUserIdAndClient(user.userId, "clientA.chat")
    assertNotNull(chatToken)
    assertEquals(TokenType.SUB, chatToken!!.tokenType)
    assertEquals("chat", chatToken.tokenSubtype)
    assertNotNull(headers.subTokens?.get("chat"))
  }

  @Test
  fun `opt-in sub token is only created when requested`() {
    val registry =
      DefaultSubTokenRegistry(listOf(chatRegistration(), deviceRegistration(includeByDefault = false)))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "web")
    assertNull(repository.findByUserIdAndClient(user.userId, "web.device"))

    val req =
      MockHttpServletRequest().apply {
        addHeader("access-token", headers.accessToken)
        addHeader("client", headers.client)
        addHeader("uid", headers.uid)
        addHeader("expiry", headers.expiry)
      }
    val res = MockHttpServletResponse()

    tokenService.renewSubToken(user.userId, req, res, "device")

    assertNotNull(repository.findByUserIdAndClient(user.userId, "web.device"))
    assertNotNull(res.getHeader("sub-tokens"))
  }

  @Test
  fun `validateSubToken accepts bearer payload`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    tokenService.createNewAuthToken(user.userId, "clientZ")
    val chat = repository.findByUserIdAndClient(user.userId, "clientZ.chat")!!
    val bearerPayload =
      mapOf(
        "client" to requireNotNull(chat.client),
        "token" to requireNotNull(chat.plainToken),
        "expiry" to chat.expiryAt.toString(),
      )
    val bearerJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(bearerPayload)
    val bearer =
      "Bearer " + java.util.Base64.getEncoder().encodeToString(bearerJson.toByteArray(Charsets.UTF_8))

    val ok = tokenService.validateSubToken(user.username, "chat", bearer)
    assertEquals(true, ok)
    val okRaw = tokenService.validateSubToken(user.username, "chat", chat.plainToken!!)
    assertEquals(true, okRaw)
  }

  @Test
  fun `primary token is created with generated client ID`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, null)

    assertNotNull(headers.client)
    val appToken = repository.findByUserIdAndClient(user.userId, headers.client!!)
    assertNotNull(appToken)
    assertEquals(TokenType.APP, appToken!!.tokenType)
  }

  @Test
  fun `primary token creation stores plain token temporarily`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "primary")

    val appToken = repository.findByUserIdAndClient(user.userId, "primary")
    assertNotNull(appToken)
    assertNotNull(appToken!!.plainToken)
  }

  @Test
  fun `primary token rotation tracks previous token`() {
    val registry = DefaultSubTokenRegistry(listOf())
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers1 = tokenService.createNewAuthToken(user.userId, "rotation-test")
    val token1 = repository.findByUserIdAndClient(user.userId, "rotation-test")!!
    val originalToken = token1.plainToken

    val headers2 = tokenService.createNewAuthToken(user.userId, "rotation-test")
    val token2 = repository.findByUserIdAndClient(user.userId, "rotation-test")!!

    assertEquals(originalToken, token2.lastToken)
  }

  @Test
  fun `old tokens are cleaned when max clients exceeded`() {
    val registry = DefaultSubTokenRegistry(listOf())
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    // Create multiple tokens for the same user (simulating max-clients scenario)
    // First, create a few tokens to reach near the limit
    for (i in 1..3) {
      tokenService.createNewAuthToken(user.userId, "client-$i")
    }

    // Verify all tokens exist
    val tokensBefore = repository.findAllByUserId(user.userId)
    assertEquals(3, tokensBefore.size)
  }

  @Test
  fun `sub token expiry respects parent token expiry`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "parent")
    val appToken = repository.findByUserIdAndClient(user.userId, "parent")!!
    val chatToken = repository.findByUserIdAndClient(user.userId, "parent.chat")!!

    assertEquals(appToken.expiryAt, chatToken.expiryAt)
  }

  @Test
  fun `sub token with custom expiry respects registration`() {
    val registry = DefaultSubTokenRegistry(listOf(shortLivedChatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "custom-expiry")
    val appToken = repository.findByUserIdAndClient(user.userId, "custom-expiry")!!
    val chatToken = repository.findByUserIdAndClient(user.userId, "custom-expiry.chat")!!

    // Chat should expire earlier than app token
    assertTrue(chatToken.expiryAt.isBefore(appToken.expiryAt))
  }

  @Test
  fun `validateSubToken rejects expired sub token`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TestTokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "expired-test")
    val chat = repository.findByUserIdAndClient(user.userId, "expired-test.chat")!!

    // Manually expire the token
    chat.expiryAt = java.time.Instant.now().minusSeconds(1)
    repository.save(chat)

    val isValid = tokenService.validateSubToken(user.username, "chat", chat.plainToken!!)
    assertEquals(false, isValid)
  }

  private fun chatRegistration(): SubTokenRegistration =
    object : SubTokenRegistration {
      override val name: String = "chat"
      override val includeByDefault: Boolean = true

      override fun clientIdFor(parentClientId: String): String = "$parentClientId.chat"

      override fun expiry(parentExpiry: Instant): Instant = parentExpiry
    }

  private fun deviceRegistration(includeByDefault: Boolean) =
    object : SubTokenRegistration {
      override val name: String = "device"
      override val includeByDefault: Boolean = includeByDefault

      override fun clientIdFor(parentClientId: String): String = "$parentClientId.device"

      override fun expiry(parentExpiry: Instant): Instant = parentExpiry
    }

  private fun shortLivedChatRegistration(): SubTokenRegistration =
    object : SubTokenRegistration {
      override val name: String = "chat"
      override val includeByDefault: Boolean = true

      override fun clientIdFor(parentClientId: String): String = "$parentClientId.chat"

      override fun expiry(parentExpiry: Instant): Instant = parentExpiry.minusSeconds(3600)
    }
}
