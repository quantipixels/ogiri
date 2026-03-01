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
package com.quantipixels.ogiri.security.redis

import com.quantipixels.ogiri.security.OgiriStubToken
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
class RedisOgiriTokenLookupCacheTest {

  companion object {
    @Container val redis: RedisContainer = RedisContainer("redis:7-alpine")
  }

  private lateinit var cache: RedisOgiriTokenLookupCache<OgiriStubToken>

  private fun defaultProperties() =
      OgiriConfigurationProperties().apply {
        lookup.apply {
          maxSize = 1000
          expiryMinutes = 5
        }
      }

  @BeforeEach
  fun setup() {
    val config = RedisStandaloneConfiguration(redis.host, redis.getMappedPort(6379))
    val factory = LettuceConnectionFactory(config).also { it.afterPropertiesSet() }
    cache = RedisOgiriTokenLookupCache(factory, defaultProperties())
    // Flush all keys before each test for isolation
    factory.connection.use { it.serverCommands().flushAll() }
  }

  @Nested
  inner class GetAndPut {
    @Test
    fun `get returns null on cache miss`() {
      assertNull(cache.get(1L, "client-a"))
    }

    @Test
    fun `put then get returns the stored token`() {
      val token = OgiriStubToken(userId = 1L, client = "client-a")
      cache.put(1L, "client-a", token)
      val retrieved = cache.get(1L, "client-a")
      assertNotNull(retrieved)
      assertEquals("client-a", retrieved?.client)
    }

    @Test
    fun `put overwrites existing entry`() {
      cache.put(1L, "c", OgiriStubToken(token = "hash1"))
      cache.put(1L, "c", OgiriStubToken(token = "hash2"))
      assertEquals("hash2", cache.get(1L, "c")?.token)
    }

    @Test
    fun `different users do not share entries`() {
      cache.put(1L, "shared", OgiriStubToken(userId = 1L))
      assertNull(cache.get(2L, "shared"))
    }
  }

  @Nested
  inner class Eviction {
    @Test
    fun `evict removes single entry`() {
      cache.put(1L, "to-evict", OgiriStubToken())
      cache.evict(1L, "to-evict")
      assertNull(cache.get(1L, "to-evict"))
    }

    @Test
    fun `evict does not affect other entries`() {
      cache.put(1L, "keep", OgiriStubToken(client = "keep"))
      cache.put(1L, "remove", OgiriStubToken(client = "remove"))
      cache.evict(1L, "remove")
      assertNotNull(cache.get(1L, "keep"))
      assertNull(cache.get(1L, "remove"))
    }

    @Test
    fun `evictAll removes all entries for a user`() {
      cache.put(1L, "c1", OgiriStubToken(client = "c1"))
      cache.put(1L, "c2", OgiriStubToken(client = "c2"))
      cache.put(2L, "c1", OgiriStubToken(userId = 2L, client = "c1"))

      cache.evictAll(1L)

      assertNull(cache.get(1L, "c1"))
      assertNull(cache.get(1L, "c2"))
      assertNotNull(cache.get(2L, "c1"), "Other user entries must not be evicted")
    }
  }
}
