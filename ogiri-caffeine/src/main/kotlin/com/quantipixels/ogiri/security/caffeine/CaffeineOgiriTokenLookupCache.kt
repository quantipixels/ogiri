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

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.tokens.OgiriToken
import java.util.concurrent.TimeUnit

/**
 * Caffeine-backed implementation of [OgiriTokenLookupCache].
 *
 * Stores token entities in an in-process Caffeine cache, eliminating repeated DB reads for the same
 * user/client within the configured TTL window.
 *
 * **Single-instance deployments only.** Each JVM holds its own cache — token revocations are not
 * visible across nodes. Use `ogiri-redis` for multi-instance deployments.
 *
 * Activated by setting:
 * ```yaml
 * ogiri:
 *   lookup:
 *     type: caffeine
 * ```
 */
class CaffeineOgiriTokenLookupCache<T : OgiriToken>(
    properties: OgiriConfigurationProperties,
) : OgiriTokenLookupCache<T> {

  private val cache: Cache<String, T> =
      Caffeine.newBuilder()
          .maximumSize(properties.lookup.maxSize)
          .expireAfterWrite(properties.lookup.expiryMinutes, TimeUnit.MINUTES)
          .build()

  private fun key(userId: Long, client: String) = "$userId:$client"

  override fun get(userId: Long, client: String): T? = cache.getIfPresent(key(userId, client))

  override fun put(userId: Long, client: String, token: T) = cache.put(key(userId, client), token)

  override fun evict(userId: Long, client: String) = cache.invalidate(key(userId, client))

  override fun evictAll(userId: Long) {
    val keys = cache.asMap().keys.filter { it.startsWith("$userId:") }
    cache.invalidateAll(keys)
  }
}
