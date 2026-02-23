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
package com.quantipixels.ogiri.security.caffeine

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.tokens.OgiriToken
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CaffeineOgiriTokenLookupCacheTest {

  private lateinit var cache: CaffeineOgiriTokenLookupCache<StubToken>

  /** Minimal OgiriToken implementation for cache unit tests. */
  data class StubToken(
      override var id: Long = 1L,
      override var userId: Long = 1L,
      override var client: String = "client",
      override var token: String = "hash",
      override var tokenType: String = "APP",
      override var expiryAt: Instant = Instant.now().plusSeconds(3600),
      override var createdAt: Instant = Instant.now(),
      override var updatedAt: Instant = Instant.now(),
      override var tokenUpdatedAt: Instant = Instant.now(),
      override var tokenSubtype: String? = null,
      override var lastToken: String? = null,
      override var previousToken: String? = null,
      override var lastUsedAt: Instant? = null,
      override var plainToken: String? = null,
  ) : OgiriToken

  private fun defaultProperties() =
      OgiriConfigurationProperties().apply {
        lookup.apply {
          maxSize = 1000
          expiryMinutes = 5
        }
      }

  @BeforeEach
  fun setup() {
    cache = CaffeineOgiriTokenLookupCache(defaultProperties())
  }

  @Nested
  inner class GetAndPut {
    @Test
    fun `get returns null on cache miss`() {
      assertNull(cache.get(1L, "client-a"))
    }

    @Test
    fun `put then get returns the stored token`() {
      val token = StubToken(userId = 1L, client = "client-a")
      cache.put(1L, "client-a", token)
      assertEquals(token, cache.get(1L, "client-a"))
    }

    @Test
    fun `put overwrites existing entry`() {
      val first = StubToken(userId = 1L, client = "c", token = "hash1")
      val second = StubToken(userId = 1L, client = "c", token = "hash2")
      cache.put(1L, "c", first)
      cache.put(1L, "c", second)
      assertEquals(second, cache.get(1L, "c"))
    }

    @Test
    fun `different users do not share entries`() {
      val tokenA = StubToken(userId = 1L, client = "shared")
      cache.put(1L, "shared", tokenA)
      assertNull(cache.get(2L, "shared"))
    }
  }

  @Nested
  inner class Eviction {
    @Test
    fun `evict removes single entry`() {
      cache.put(1L, "to-evict", StubToken())
      cache.evict(1L, "to-evict")
      assertNull(cache.get(1L, "to-evict"))
    }

    @Test
    fun `evict does not affect other entries`() {
      cache.put(1L, "keep", StubToken(client = "keep"))
      cache.put(1L, "remove", StubToken(client = "remove"))
      cache.evict(1L, "remove")
      assertNotNull(cache.get(1L, "keep"))
      assertNull(cache.get(1L, "remove"))
    }

    @Test
    fun `evictAll removes all entries for a user`() {
      cache.put(1L, "c1", StubToken(client = "c1"))
      cache.put(1L, "c2", StubToken(client = "c2"))
      cache.put(2L, "c1", StubToken(userId = 2L, client = "c1"))

      cache.evictAll(1L)

      assertNull(cache.get(1L, "c1"))
      assertNull(cache.get(1L, "c2"))
      assertNotNull(cache.get(2L, "c1"), "Other user entries must not be evicted")
    }
  }
}
