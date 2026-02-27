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
import com.quantipixels.ogiri.security.spi.NoOpOgiriAuditHook
import com.quantipixels.ogiri.security.spi.NoOpOgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Tests for the setter injection pattern on OgiriTokenService.
 *
 * Verifies that optional collaborators (auditHook, rateLimitHook, lookupCache) can be injected
 * after construction via open setter methods, following the Spring Security AuthorizationFilter
 * gold standard.
 */
@Tag("unit")
class OgiriTokenServiceSetterInjectionTest {

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

  /** Creates a minimal 6-arg service with no optional collaborators set. */
  private fun createService(): OgiriTokenService<TestToken> =
      object :
          OgiriTokenService<TestToken>(
              repository,
              passwordEncoder,
              userDirectory,
              identifierPolicy,
              DefaultOgiriSubTokenRegistry(emptyList()),
              defaultProperties(),
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

  // ---------------------------------------------------------------------------
  // Slice 1: No-op singleton objects are public and usable
  // ---------------------------------------------------------------------------

  @Nested
  inner class NoOpSingletonTests {

    @Test
    fun `NoOpOgiriAuditHook is a singleton object that implements OgiriAuditHook`() {
      // Verify it compiles as OgiriAuditHook and calling methods does nothing
      val hook: OgiriAuditHook = NoOpOgiriAuditHook
      hook.onLoginSuccess(1L, "client", "127.0.0.1")
      hook.onLoginFailure("user", "reason", null)
      hook.onTokenRotated(1L, "client")
      hook.onTokenRevoked(1L, "client")
      hook.onSubTokenCreated(1L, "parent", "sub")
      // If we reach here without exception, the no-op object works correctly
      assertTrue(true)
    }

    @Test
    fun `NoOpOgiriRateLimitHook is a singleton object that implements OgiriRateLimitHook`() {
      // Verify it compiles as OgiriRateLimitHook and calling methods does nothing
      val hook: OgiriRateLimitHook = NoOpOgiriRateLimitHook
      // Rate limit hooks need HttpServletRequest — just verify the object identity is stable
      // and the object reference is correct type
      assertNotNull(hook)
      assertTrue(hook is OgiriRateLimitHook)
    }

    @Test
    fun `NoOpOgiriAuditHook is a stable singleton reference`() {
      val ref1: OgiriAuditHook = NoOpOgiriAuditHook
      val ref2: OgiriAuditHook = NoOpOgiriAuditHook
      assertTrue(ref1 === ref2, "NoOpOgiriAuditHook must be a singleton (same reference)")
    }

    @Test
    fun `NoOpOgiriRateLimitHook is a stable singleton reference`() {
      val ref1: OgiriRateLimitHook = NoOpOgiriRateLimitHook
      val ref2: OgiriRateLimitHook = NoOpOgiriRateLimitHook
      assertTrue(ref1 === ref2, "NoOpOgiriRateLimitHook must be a singleton (same reference)")
    }
  }

  // ---------------------------------------------------------------------------
  // Slice 2: 6-arg constructor works — service functional without setters called
  // ---------------------------------------------------------------------------

  @Nested
  inner class SixArgConstructorTests {

    @Test
    fun `service constructed with 6 args is functional`() {
      val service = createService()
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-a")
      assertNotNull(headers.accessToken)
    }

    @Test
    fun `service without audit hook does not throw on login`() {
      val service = createService()
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "no-hook-client")
      assertNotNull(headers.accessToken)
    }
  }

  // ---------------------------------------------------------------------------
  // Slice 3: setAuditHook — post-construction audit hook is invoked
  // ---------------------------------------------------------------------------

  @Nested
  inner class SetAuditHookTests {

    @Test
    fun `setAuditHook wires audit hook that fires on token rotation`() {
      val service = createService()
      val calls = mutableListOf<String>()
      service.setAuditHook(
          object : OgiriAuditHook {
            override fun onTokenRotated(userId: Long, client: String) {
              calls.add("rotated:$userId:$client")
            }
          })

      val expiry = Instant.now().plusSeconds(3600)
      service.createOrUpdateToken(user, "setter-client", expiry)
      assertTrue(calls.isEmpty(), "First creation is not a rotation")
      service.createOrUpdateToken(user, "setter-client", expiry)
      assertEquals(1, calls.size)
      assertEquals("rotated:1:setter-client", calls[0])
    }

    @Test
    fun `setAuditHook replaces previously set hook`() {
      val service = createService()
      val firstCalls = mutableListOf<String>()
      val secondCalls = mutableListOf<String>()

      service.setAuditHook(
          object : OgiriAuditHook {
            override fun onTokenRotated(userId: Long, client: String) {
              firstCalls.add("rotated")
            }
          })

      service.setAuditHook(
          object : OgiriAuditHook {
            override fun onTokenRotated(userId: Long, client: String) {
              secondCalls.add("rotated")
            }
          })

      val expiry = Instant.now().plusSeconds(3600)
      service.createOrUpdateToken(user, "replace-client", expiry)
      service.createOrUpdateToken(user, "replace-client", expiry)

      assertTrue(firstCalls.isEmpty(), "First hook must be replaced and not called")
      assertEquals(1, secondCalls.size, "Second hook must be called")
    }
  }

  // ---------------------------------------------------------------------------
  // Slice 4: setRateLimitHook — post-construction rate limit hook is invoked
  // ---------------------------------------------------------------------------

  @Nested
  inner class SetRateLimitHookTests {

    @Test
    fun `setRateLimitHook wires rate limit hook that fires before token creation`() {
      val service = createService()
      val calls = mutableListOf<String>()
      service.setRateLimitHook(
          object : OgiriRateLimitHook {
            override fun beforeTokenCreation(
                request: jakarta.servlet.http.HttpServletRequest,
                userId: Long,
            ) {
              calls.add("beforeTokenCreation:$userId")
            }
          })

      val request =
          org.springframework.mock.web.MockHttpServletRequest().apply { remoteAddr = "127.0.0.1" }
      service.createNewAuthToken(user.getOgiriUserId(), "rate-client", request)

      assertEquals(1, calls.size)
      assertEquals("beforeTokenCreation:1", calls[0])
    }
  }

  // ---------------------------------------------------------------------------
  // Slice 5: setLookupCache — post-construction cache is consulted
  // ---------------------------------------------------------------------------

  @Nested
  inner class SetLookupCacheTests {

    private inner class RecordingCache : OgiriTokenLookupCache<TestToken> {
      val getCalls = mutableListOf<String>()
      val putCalls = mutableListOf<String>()
      val store = mutableMapOf<String, TestToken>()

      private fun key(userId: Long, client: String) = "$userId:$client"

      override fun get(userId: Long, client: String): TestToken? {
        getCalls.add(key(userId, client))
        return store[key(userId, client)]
      }

      override fun put(userId: Long, client: String, token: TestToken) {
        putCalls.add(key(userId, client))
        store[key(userId, client)] = token
      }

      override fun evict(userId: Long, client: String) {
        store.remove(key(userId, client))
      }

      override fun evictAll(userId: Long) {
        store.keys.removeIf { it.startsWith("$userId:") }
      }
    }

    @Test
    fun `setLookupCache wires cache that is consulted on token lookup`() {
      val service = createService()
      val cache = RecordingCache()
      service.setLookupCache(cache)

      val headers = service.createNewAuthToken(user.getOgiriUserId(), "cache-client")
      val client = headers.client!!
      val token = headers.accessToken!!

      cache.getCalls.clear()
      cache.putCalls.clear()

      service.validToken(token, user, client)

      assertTrue(
          cache.getCalls.isNotEmpty(), "Cache.get must be called after setLookupCache was used")
    }

    @Test
    fun `service without setLookupCache falls through to repository`() {
      val service = createService()
      // no setLookupCache called — should still work
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "no-cache-client")
      assertNotNull(headers.accessToken)
    }
  }
}
