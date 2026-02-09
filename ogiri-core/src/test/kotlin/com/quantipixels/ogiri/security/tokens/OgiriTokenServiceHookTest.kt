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
import com.quantipixels.ogiri.security.spi.OgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import com.quantipixels.ogiri.security.testutil.emptyObjectProvider
import com.quantipixels.ogiri.security.testutil.objectProviderOf
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder

class OgiriTokenServiceHookTest {
  private lateinit var repository: InMemoryTokenRepository
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

  private val user = TestFixtures.testUser(userId = 1L, username = "testuser")
  private val userDirectory =
      object : OgiriUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

        override fun findByEmail(email: String) = user.takeIf { "testuser@example.com" == email }

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

  private fun createService(
      auditHook: OgiriAuditHook? = null,
      rateLimitHook: OgiriRateLimitHook? = null,
      registry: OgiriSubTokenRegistry = DefaultOgiriSubTokenRegistry(emptyList()),
  ): OgiriTokenService<TestToken> =
      TestTokenService(
          repository = repository,
          passwordEncoder = passwordEncoder,
          userDirectory = userDirectory,
          identifierPolicy = identifierPolicy,
          subTokenRegistry = registry,
          properties = defaultProperties(),
          auditHook = auditHook,
          rateLimitHook = rateLimitHook,
      )

  private inner class TestTokenService(
      repository: OgiriTokenRepository<TestToken>,
      passwordEncoder: PasswordEncoder,
      userDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
      auditHook: OgiriAuditHook? = null,
      rateLimitHook: OgiriRateLimitHook? = null,
  ) :
      OgiriTokenService<TestToken>(
          repository,
          passwordEncoder,
          userDirectory,
          identifierPolicy,
          subTokenRegistry,
          properties,
          auditHook?.let { objectProviderOf(it) } ?: emptyObjectProvider(),
          rateLimitHook?.let { objectProviderOf(it) } ?: emptyObjectProvider(),
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

  @BeforeEach
  fun setup() {
    repository = InMemoryTokenRepository()
  }

  @AfterEach
  fun cleanup() {
    SecurityContextHolder.clearContext()
  }

  @Nested
  inner class ObjectProviderFallbackTests {
    @Test
    fun `service works with no hook beans (empty ObjectProvider uses no-op defaults)`() {
      val service = createService()
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-a")
      assertTrue(headers.accessToken != null)
    }

    @Test
    fun `service uses provided audit hook when available`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onLoginSuccess(userId: Long, client: String, ip: String?) {
              calls.add("loginSuccess:$userId")
            }

            override fun onLoginFailure(identifier: String, reason: String, ip: String?) {
              calls.add("loginFailure:$identifier:$reason")
            }
          }
      val service = createService(auditHook = auditHook)
      service.createNewAuthToken(user.getOgiriUserId(), "client-b")
      // createNewAuthToken doesn't trigger login hooks, just verifies no errors
      assertTrue(calls.isEmpty())
    }
  }

  @Nested
  inner class AuditHookInvocationTests {
    @Test
    fun `verifyUser success calls onLoginSuccess`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onLoginSuccess(userId: Long, client: String, ip: String?) {
              calls.add("loginSuccess:$userId:$client")
            }
          }
      val service = createService(auditHook = auditHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      service.verifyUser(request, response, "testuser@example.com", "password")

      assertEquals(1, calls.size)
      assertTrue(calls[0].startsWith("loginSuccess:1:"))
    }

    @Test
    fun `verifyUser failure (user not found) calls onLoginFailure`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onLoginFailure(identifier: String, reason: String, ip: String?) {
              calls.add("loginFailure:$identifier:$reason")
            }
          }
      val service = createService(auditHook = auditHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      assertThrows(SecurityServiceException::class.java) {
        service.verifyUser(request, response, "nobody@example.com", "password")
      }

      assertEquals(1, calls.size)
      assertEquals("loginFailure:nobody@example.com:user_not_found", calls[0])
    }

    @Test
    fun `verifyUser failure (bad password) calls onLoginFailure`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onLoginFailure(identifier: String, reason: String, ip: String?) {
              calls.add("loginFailure:$identifier:$reason")
            }
          }
      val service = createService(auditHook = auditHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      assertThrows(SecurityServiceException::class.java) {
        service.verifyUser(request, response, "testuser@example.com", "wrong-password")
      }

      assertEquals(1, calls.size)
      assertEquals("loginFailure:testuser@example.com:invalid_password", calls[0])
    }

    @Test
    fun `createOrUpdateToken rotation calls onTokenRotated`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onTokenRotated(userId: Long, client: String) {
              calls.add("rotated:$userId:$client")
            }
          }
      val service = createService(auditHook = auditHook)
      val expiry = Instant.now().plusSeconds(3600)

      // First create (not a rotation)
      service.createOrUpdateToken(user, "client-rot", expiry)
      assertTrue(calls.isEmpty())

      // Second create with same client = rotation
      service.createOrUpdateToken(user, "client-rot", expiry)
      assertEquals(1, calls.size)
      assertEquals("rotated:1:client-rot", calls[0])
    }

    @Test
    fun `revokeClient calls onTokenRevoked`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onTokenRevoked(userId: Long, client: String) {
              calls.add("revoked:$userId:$client")
            }
          }
      val service = createService(auditHook = auditHook)

      val headers = service.createNewAuthToken(user.getOgiriUserId(), "revoke-client")

      val request =
          MockHttpServletRequest().apply {
            addHeader("access-token", headers.accessToken)
            addHeader("client", headers.client)
            addHeader("uid", headers.uid)
            addHeader("expiry", headers.expiry)
          }
      val response = MockHttpServletResponse()

      service.revokeClient(user.getOgiriUserId(), request, response)

      assertEquals(1, calls.size)
      assertEquals("revoked:1:revoke-client", calls[0])
    }

    @Test
    fun `issueSubTokens calls onSubTokenCreated for new sub-tokens`() {
      val calls = mutableListOf<String>()
      val auditHook =
          object : OgiriAuditHook {
            override fun onSubTokenCreated(
                userId: Long,
                parentClient: String,
                subTokenName: String,
            ) {
              calls.add("subCreated:$userId:$parentClient:$subTokenName")
            }
          }
      val registry =
          DefaultOgiriSubTokenRegistry(
              listOf(
                  object : OgiriSubTokenRegistration {
                    override val name = "chat"
                    override val includeByDefault = true

                    override fun clientIdFor(parentClientId: String) = "$parentClientId.chat"

                    override fun expiry(parentExpiry: Instant) = parentExpiry
                  }))
      val service = createService(auditHook = auditHook, registry = registry)

      service.createNewAuthToken(user.getOgiriUserId(), "parent-client")

      assertTrue(calls.any { it == "subCreated:1:parent-client:chat" })
    }
  }

  @Nested
  inner class RateLimitHookInvocationTests {
    @Test
    fun `verifyUser calls beforeLogin`() {
      val calls = mutableListOf<String>()
      val rateLimitHook =
          object : OgiriRateLimitHook {
            override fun beforeLogin(request: HttpServletRequest, identifier: String) {
              calls.add("beforeLogin:$identifier")
            }
          }
      val service = createService(rateLimitHook = rateLimitHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      service.verifyUser(request, response, "testuser@example.com", "password")

      assertEquals(1, calls.size)
      assertEquals("beforeLogin:testuser@example.com", calls[0])
    }

    @Test
    fun `rate limit hook throwing SecurityServiceException prevents login`() {
      val rateLimitHook =
          object : OgiriRateLimitHook {
            override fun beforeLogin(request: HttpServletRequest, identifier: String) {
              throw SecurityServiceException("error.auth.rate_limited")
            }
          }
      val service = createService(rateLimitHook = rateLimitHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      val exception =
          assertThrows(SecurityServiceException::class.java) {
            service.verifyUser(request, response, "testuser@example.com", "password")
          }
      assertEquals("error.auth.rate_limited", exception.message)
    }

    @Test
    fun `createNewAuthToken with request calls beforeTokenCreation`() {
      val calls = mutableListOf<String>()
      val rateLimitHook =
          object : OgiriRateLimitHook {
            override fun beforeTokenCreation(request: HttpServletRequest, userId: Long) {
              calls.add("beforeTokenCreation:$userId:${request.remoteAddr}")
            }
          }
      val service = createService(rateLimitHook = rateLimitHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-hook", request)

      assertTrue(headers.accessToken != null)
      assertEquals(1, calls.size)
      assertEquals("beforeTokenCreation:1:127.0.0.1", calls[0])
    }

    @Test
    fun `verifyUser login flow triggers beforeTokenCreation`() {
      val calls = mutableListOf<String>()
      val rateLimitHook =
          object : OgiriRateLimitHook {
            override fun beforeLogin(request: HttpServletRequest, identifier: String) {
              calls.add("beforeLogin:$identifier")
            }

            override fun beforeTokenCreation(request: HttpServletRequest, userId: Long) {
              calls.add("beforeTokenCreation:$userId")
            }
          }
      val service = createService(rateLimitHook = rateLimitHook)

      val request = MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      val response = MockHttpServletResponse()

      service.verifyUser(request, response, "testuser@example.com", "password")

      assertEquals(2, calls.size)
      assertEquals("beforeLogin:testuser@example.com", calls[0])
      assertEquals("beforeTokenCreation:1", calls[1])
    }
  }
}
