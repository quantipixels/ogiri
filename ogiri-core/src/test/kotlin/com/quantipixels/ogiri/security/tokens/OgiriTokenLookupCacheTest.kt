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
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class OgiriTokenLookupCacheTest {
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

  private val user = TestFixtures.testUser(userId = 1L)
  private val userDirectory =
      object : OgiriUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

        override fun findByEmail(email: String) = user

        override fun findByUsername(username: String) = user

        override fun recordSuccessfulLogin(userId: Long) {}
      }

  /** Simple in-memory cache that records all operations for assertion. */
  private inner class RecordingCache : OgiriTokenLookupCache<TestToken> {
    val store = mutableMapOf<String, TestToken>()
    val getCalls = mutableListOf<String>()
    val putCalls = mutableListOf<String>()
    val evictCalls = mutableListOf<String>()
    val evictAllCalls = mutableListOf<Long>()

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
      evictCalls.add(key(userId, client))
      store.remove(key(userId, client))
    }

    override fun evictAll(userId: Long) {
      evictAllCalls.add(userId)
      store.keys.removeIf { it.startsWith("$userId:") }
    }
  }

  private fun createService(
      cache: OgiriTokenLookupCache<TestToken>? = null
  ): OgiriTokenService<TestToken> =
      object :
          OgiriTokenService<TestToken>(
              repository,
              passwordEncoder,
              userDirectory,
              identifierPolicy,
              DefaultOgiriSubTokenRegistry(emptyList()),
              OgiriConfigurationProperties().apply {
                auth.apply {
                  maxClients = 24
                  batchGraceSeconds = 5
                  tokenLifespanDays = 14
                }
              },
              lookupCache = cache,
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

  @Nested
  inner class NoCacheTests {
    @Test
    fun `service works with no lookup cache (falls through to repository)`() {
      val service = createService(cache = null)
      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-a")
      assertNotNull(headers.accessToken)
    }
  }

  @Nested
  inner class CachePopulationTests {
    @Test
    fun `token is in cache after createNewAuthToken`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-x")
      val client = headers.client!!

      // The token should be in the cache after creation/lookup in issueSubTokens
      assertNotNull(cache.store["1:$client"], "Cache should hold the token entity after creation")
    }

    @Test
    fun `validToken is served from cache without going to repo on second call`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-y")
      val client = headers.client!!
      val token = headers.accessToken!!

      // Confirm cache is populated
      assertNotNull(cache.store["1:$client"])

      // First validToken: cache hit → getCalls incremented, no put
      cache.getCalls.clear()
      cache.putCalls.clear()

      service.validToken(token, user, client)

      assertEquals(listOf("1:$client"), cache.getCalls, "Cache.get should be called once")
      assertEquals(emptyList<String>(), cache.putCalls, "No put on cache hit")
    }

    @Test
    fun `validToken populates cache on miss (after manual evict)`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val headers = service.createNewAuthToken(user.getOgiriUserId(), "client-z")
      val client = headers.client!!
      val token = headers.accessToken!!

      // Manually evict to simulate a cold cache for this entry
      cache.store.remove("1:$client")
      cache.getCalls.clear()
      cache.putCalls.clear()

      service.validToken(token, user, client)

      assertEquals(listOf("1:$client"), cache.getCalls, "Cache.get called on miss")
      assertEquals(listOf("1:$client"), cache.putCalls, "Cache populated on miss")
    }
  }

  @Nested
  inner class CacheEvictionTests {
    @Test
    fun `createOrUpdateToken evicts cache for the rotated client`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val expiry = Instant.now().plusSeconds(3600)
      service.createOrUpdateToken(user, "evict-client", expiry)
      val evictsAfterCreate = cache.evictCalls.size

      service.createOrUpdateToken(user, "evict-client", expiry)

      assertEquals(evictsAfterCreate + 1, cache.evictCalls.size)
      assert(cache.evictCalls.last() == "1:evict-client")
    }

    @Test
    fun `deleteToken(client) evicts that client from cache`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val expiry = Instant.now().plusSeconds(3600)
      service.createOrUpdateToken(user, "del-client", expiry)
      cache.evictCalls.clear()

      service.deleteToken(user.getOgiriUserId(), "del-client")

      assertEquals(listOf("1:del-client"), cache.evictCalls)
      assertNull(cache.store["1:del-client"])
    }

    @Test
    fun `deleteAllForUser evicts all entries for that user`() {
      val cache = RecordingCache()
      val service = createService(cache)

      val expiry = Instant.now().plusSeconds(3600)
      service.createOrUpdateToken(user, "c1", expiry)
      service.createOrUpdateToken(user, "c2", expiry)
      cache.evictAllCalls.clear()

      service.deleteAllForUser(user.getOgiriUserId())

      assertEquals(listOf(1L), cache.evictAllCalls)
    }
  }
}
