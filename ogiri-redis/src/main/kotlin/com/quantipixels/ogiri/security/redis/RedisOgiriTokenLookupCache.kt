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
import com.quantipixels.ogiri.security.spi.OgiriCacheKey
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.tokens.OgiriToken
import java.time.Duration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis-backed implementation of [OgiriTokenLookupCache].
 *
 * Stores token entities as JSON in Redis, shared across all application nodes. Token revocations
 * are immediately visible to every instance.
 *
 * Serialization uses [GenericJackson2JsonRedisSerializer], which embeds the concrete class name in
 * JSON so the correct `T` subtype is restored on deserialization — no per-deployment type
 * configuration needed.
 *
 * **Key layout:** `ogiri:token:{userId}:{client}`
 *
 * **evictAll** uses Redis `SCAN` rather than `KEYS` to avoid blocking the server on large
 * keyspaces.
 *
 * Activated by setting:
 * ```yaml
 * ogiri:
 *   lookup:
 *     type: redis
 * ```
 */
class RedisOgiriTokenLookupCache<T : OgiriToken>(
    connectionFactory: RedisConnectionFactory,
    properties: OgiriConfigurationProperties,
) : OgiriTokenLookupCache<T> {

  private val ttl: Duration = Duration.ofMinutes(properties.lookup.expiryMinutes)

  private val template: RedisTemplate<String, T> = buildTemplate(connectionFactory)

  override fun get(userId: Long, client: String): T? =
      template.opsForValue().get(OgiriCacheKey.key(userId, client))

  override fun put(userId: Long, client: String, token: T) =
      template.opsForValue().set(OgiriCacheKey.key(userId, client), token, ttl)

  override fun evict(userId: Long, client: String) {
    template.delete(OgiriCacheKey.key(userId, client))
  }

  /**
   * Remove all cached token entries for a user via Redis SCAN.
   *
   * SCAN iterates the keyspace incrementally, avoiding the server block that KEYS causes on large
   * datasets.
   */
  override fun evictAll(userId: Long) {
    val prefix = OgiriCacheKey.prefix(userId)
    val options = ScanOptions.scanOptions().match("$prefix*").count(100).build()
    val keysToDelete = mutableListOf<String>()
    template.execute { connection ->
      connection.keyCommands().scan(options).use { cursor ->
        cursor.forEachRemaining { key -> keysToDelete.add(String(key)) }
      }
    }
    if (keysToDelete.isNotEmpty()) {
      template.delete(keysToDelete)
    }
  }

  companion object {
    @Suppress("UNCHECKED_CAST")
    private fun <T : OgiriToken> buildTemplate(
        connectionFactory: RedisConnectionFactory,
    ): RedisTemplate<String, T> =
        (RedisTemplate<String, Any>().also { t ->
          t.connectionFactory = connectionFactory
          t.keySerializer = StringRedisSerializer()
          t.valueSerializer = GenericJackson2JsonRedisSerializer()
          t.afterPropertiesSet()
        } as RedisTemplate<String, T>)
  }
}
