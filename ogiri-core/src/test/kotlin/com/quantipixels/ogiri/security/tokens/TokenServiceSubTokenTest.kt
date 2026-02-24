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
import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.crypto.password.PasswordEncoder

class TokenServiceSubTokenTest {
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

  private fun defaultAuthProperties() =
      OgiriConfigurationProperties().apply {
        auth.apply {
          maxClients = 24
          batchGraceSeconds = 5
          tokenLifespanDays = 14
        }
      }

  // Custom TokenService that implements tokenFactory for TestToken
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
          properties) {
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

  @BeforeEach
  fun setup() {
    repository = InMemoryTokenRepository()
  }

  @Test
  fun `default sub token is issued and returned in headers`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers: AuthHeader = tokenService.createNewAuthToken(user.getOgiriUserId(), "clientA")

    val chatToken =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "clientA.chat").orElse(null)
    assertNotNull(chatToken)
    assertEquals(OgiriTokenType.SUB, OgiriTokenType.of(chatToken!!.tokenType))
    assertEquals("chat", chatToken.tokenSubtype)
    assertNotNull(headers.subTokens?.get("chat"))
  }

  @Test
  fun `opt-in sub token is only created when requested`() {
    val registry =
        DefaultOgiriSubTokenRegistry(
            listOf(chatRegistration(), deviceRegistration(includeByDefault = false)))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "web")
    assertFalse(repository.findByUserIdAndClient(user.getOgiriUserId(), "web.device").isPresent)

    val req =
        MockHttpServletRequest().apply {
          addHeader("access-token", headers.accessToken)
          addHeader("client", headers.client)
          addHeader("uid", headers.uid)
          addHeader("expiry", headers.expiry)
        }
    val res = MockHttpServletResponse()

    tokenService.renewSubToken(user.getOgiriUserId(), req, res, "device")

    assertTrue(repository.findByUserIdAndClient(user.getOgiriUserId(), "web.device").isPresent)
    assertNotNull(res.getHeader("chat"))
  }

  @Test
  fun `validateSubToken accepts bearer payload`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    tokenService.createNewAuthToken(user.getOgiriUserId(), "clientZ")
    val chat =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "clientZ.chat").orElse(null)!!
    val bearerPayload =
        mapOf(
            "client" to requireNotNull(chat.client),
            "token" to requireNotNull(chat.plainToken),
            "expiry" to chat.expiryAt.toString(),
        )
    val bearerJson =
        com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(bearerPayload)
    val bearer =
        "Bearer " +
            java.util.Base64.getEncoder().encodeToString(bearerJson.toByteArray(Charsets.UTF_8))

    val ok = tokenService.validateSubToken(user.username, "chat", bearer)
    assertEquals(true, ok)
    val okRaw = tokenService.validateSubToken(user.username, "chat", chat.plainToken!!)
    assertEquals(true, okRaw)
  }

  @Test
  fun `primary token is created with generated client ID`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), null)

    assertNotNull(headers.client)
    val appToken =
        repository.findByUserIdAndClient(user.getOgiriUserId(), headers.client!!).orElse(null)
    assertNotNull(appToken)
    assertEquals(OgiriTokenType.APP, OgiriTokenType.of(appToken!!.tokenType))
  }

  @Test
  fun `primary token creation stores plain token temporarily`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "primary")

    val appToken = repository.findByUserIdAndClient(user.getOgiriUserId(), "primary").orElse(null)
    assertNotNull(appToken)
    assertNotNull(appToken!!.plainToken)
  }

  @Test
  fun `primary token rotation tracks previous token`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf())
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers1 = tokenService.createNewAuthToken(user.getOgiriUserId(), "rotation-test")
    val token1 =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "rotation-test").orElse(null)!!
    val originalToken = token1.plainToken

    val headers2 = tokenService.createNewAuthToken(user.getOgiriUserId(), "rotation-test")
    val token2 =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "rotation-test").orElse(null)!!

    assertEquals(originalToken, token2.lastToken)
  }

  @Test
  fun `old tokens are cleaned when max clients exceeded`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf())
    val properties = defaultAuthProperties().apply { auth.maxClients = 2 }
    tokenService =
        TestTokenService(
            repository, passwordEncoder, userDirectory, identifierPolicy, registry, properties)

    // Create 3 tokens for the same user, with small delays to ensure different timestamps
    tokenService.createNewAuthToken(user.getOgiriUserId(), "client-1")
    repository.incrementClock() // deterministic clock bump instead of wall-clock sleep
    tokenService.createNewAuthToken(user.getOgiriUserId(), "client-2")
    repository.incrementClock()
    tokenService.createNewAuthToken(user.getOgiriUserId(), "client-3")
    // Verify only 2 tokens exist (the most recent ones)
    val tokensAfter = repository.findByUserIdOrderByUpdatedAtDesc(user.getOgiriUserId())
    assertEquals(2, tokensAfter.size)
    assertFalse(repository.findByUserIdAndClient(user.getOgiriUserId(), "client-1").isPresent)
    assertTrue(repository.findByUserIdAndClient(user.getOgiriUserId(), "client-2").isPresent)
    assertTrue(repository.findByUserIdAndClient(user.getOgiriUserId(), "client-3").isPresent)
  }

  @Test
  fun `sub token expiry respects parent token expiry`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "parent")
    val appToken = repository.findByUserIdAndClient(user.getOgiriUserId(), "parent").orElse(null)!!
    val chatToken =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "parent.chat").orElse(null)!!

    assertEquals(appToken.expiryAt, chatToken.expiryAt)
  }

  @Test
  fun `sub token with custom expiry respects registration`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(shortLivedChatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "custom-expiry")
    val appToken =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "custom-expiry").orElse(null)!!
    val chatToken =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "custom-expiry.chat").orElse(null)!!

    // Chat should expire earlier than app token
    assertTrue(chatToken.expiryAt.isBefore(appToken.expiryAt))
  }

  @Test
  fun `validateSubToken rejects expired sub token`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "expired-test")
    val chat =
        repository.findByUserIdAndClient(user.getOgiriUserId(), "expired-test.chat").orElse(null)!!

    // Manually expire the token
    chat.expiryAt = java.time.Instant.now().minusSeconds(1)
    repository.save(chat)

    val isValid = tokenService.validateSubToken(user.username, "chat", chat.plainToken!!)
    assertEquals(false, isValid)
  }

  @Test
  fun `renewSubToken helper returns single sub-token header`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(deviceRegistration(includeByDefault = true)))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    val headers = tokenService.createNewAuthToken(user.getOgiriUserId(), "renewal")
    val renewed = tokenService.renewSubToken(user.getOgiriUserId(), headers.client!!, "device")

    assertNotNull(renewed?.subTokens?.get("device"))
    assertEquals("renewal.device", renewed?.subTokens?.get("device")?.client)
  }

  @Test
  fun `getSubToken returns stored base token`() {
    val registry = DefaultOgiriSubTokenRegistry(listOf(chatRegistration()))
    tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            registry,
            defaultAuthProperties())

    tokenService.createNewAuthToken(user.getOgiriUserId(), "base-client")
    val stored = tokenService.getSubToken(user.getOgiriUserId(), "chat")

    assertNotNull(stored)
    assertEquals("base-client.chat", stored!!.client)
  }

  private fun chatRegistration(): OgiriSubTokenRegistration =
      object : OgiriSubTokenRegistration {
        override val name: String = "chat"
        override val includeByDefault: Boolean = true

        override fun clientIdFor(parentClientId: String): String = "$parentClientId.chat"

        override fun expiry(parentExpiry: Instant): Instant = parentExpiry
      }

  private fun deviceRegistration(includeByDefault: Boolean) =
      object : OgiriSubTokenRegistration {
        override val name: String = "device"
        override val includeByDefault: Boolean = includeByDefault

        override fun clientIdFor(parentClientId: String): String = "$parentClientId.device"

        override fun expiry(parentExpiry: Instant): Instant = parentExpiry
      }

  private fun shortLivedChatRegistration(): OgiriSubTokenRegistration =
      object : OgiriSubTokenRegistration {
        override val name: String = "chat"
        override val includeByDefault: Boolean = true

        override fun clientIdFor(parentClientId: String): String = "$parentClientId.chat"

        override fun expiry(parentExpiry: Instant): Instant = parentExpiry.minusSeconds(3600)
      }
}
