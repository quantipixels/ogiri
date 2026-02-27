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

class OgiriCaffeineAutoConfigurationTest {

  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OgiriCaffeineAutoConfiguration::class.java))
          .withUserConfiguration(TestConfig::class.java)

  @Configuration
  @EnableConfigurationProperties(OgiriConfigurationProperties::class)
  class TestConfig

  @Nested
  inner class Activation {
    @Test
    fun `activates with lowercase caffeine`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=caffeine").run { context ->
        assertTrue(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `activates with title case Caffeine`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=Caffeine").run { context ->
        assertTrue(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `activates with uppercase CAFFEINE`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=CAFFEINE").run { context ->
        assertTrue(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `activates with mixed whitespace`() {
      contextRunner.withPropertyValues("ogiri.lookup.type= Caffeine ").run { context ->
        assertTrue(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }
  }

  @Nested
  inner class NonActivation {
    @Test
    fun `does not activate when type is absent`() {
      contextRunner.run { context ->
        assertFalse(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `does not activate when type is redis`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=redis").run { context ->
        assertFalse(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `does not activate when type is unrecognized`() {
      contextRunner.withPropertyValues("ogiri.lookup.type=memcached").run { context ->
        assertFalse(context.containsBean("ogiriCaffeineTokenLookupCache"))
      }
    }

    @Test
    fun `custom OgiriTokenLookupCache bean wins over auto-configured caffeine`() {
      contextRunner
          .withPropertyValues("ogiri.lookup.type=caffeine")
          .withUserConfiguration(CustomLookupCacheConfig::class.java)
          .run { context ->
            assertFalse(context.containsBean("ogiriCaffeineTokenLookupCache"))
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
