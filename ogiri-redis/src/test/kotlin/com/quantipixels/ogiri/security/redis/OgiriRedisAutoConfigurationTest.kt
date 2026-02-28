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

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.tokens.OgiriToken
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class OgiriRedisAutoConfigurationTest {

  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OgiriRedisAutoConfiguration::class.java))
          .withUserConfiguration(TestConfig::class.java)

  @Configuration
  @EnableConfigurationProperties(OgiriConfigurationProperties::class)
  class TestConfig {
    /** Stub connection factory — auto-config condition evaluation does not open a connection. */
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory =
        LettuceConnectionFactory(RedisStandaloneConfiguration("localhost", 6379))
  }

  @Nested
  inner class Activation {
    @Test
    fun `activates with lowercase redis`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=redis").run { context ->
        assertTrue(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `activates with title case Redis`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=Redis").run { context ->
        assertTrue(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `activates with uppercase REDIS`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=REDIS").run { context ->
        assertTrue(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `activates with mixed whitespace`() {
      contextRunner.withPropertyValues("ogiri.lookup.type= Redis ").run { context ->
        assertTrue(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }
  }

  @Nested
  inner class NonActivation {
    @Test
    fun `does not activate when type is absent`() {
      contextRunner.run { context ->
        assertFalse(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `does not activate when type is caffeine`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=caffeine").run { context ->
        assertFalse(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `does not activate when type is unrecognized`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=memcached").run { context ->
        assertFalse(context.containsBean("ogiriRedisTokenLookupCache"))
      }
    }

    @Test
    fun `custom OgiriTokenLookupCache bean wins over auto-configured redis`() {
      contextRunner
          .withPropertyValues("ogiri.lookup.type=redis")
          .withUserConfiguration(CustomLookupCacheConfig::class.java)
          .run { context ->
            assertFalse(context.containsBean("ogiriRedisTokenLookupCache"))
            assertTrue(context.containsBean("customLookupCache"))
          }
    }
  }

  @Configuration
  class CustomLookupCacheConfig {
    @Bean fun customLookupCache(): OgiriTokenLookupCache<OgiriToken> = StubLookupCache()
  }

  private class StubLookupCache : OgiriTokenLookupCache<OgiriToken> {
    override fun get(userId: Long, client: String): OgiriToken? = null

    override fun put(userId: Long, client: String, token: OgiriToken) {}

    override fun evict(userId: Long, client: String) {}

    override fun evictAll(userId: Long) {}
  }
}
