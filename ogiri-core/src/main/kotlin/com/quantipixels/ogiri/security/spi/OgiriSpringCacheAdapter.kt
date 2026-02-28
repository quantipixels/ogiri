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
package com.quantipixels.ogiri.security.spi

import com.quantipixels.ogiri.security.tokens.OgiriToken
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

/**
 * Bridges an existing Spring [CacheManager] into the [OgiriTokenLookupCache] SPI.
 *
 * Activate by setting `ogiri.cache.use-spring-cache-manager=true` in your application
 * configuration. Ogiri will detect the existing [CacheManager] bean and wrap it automatically — no
 * additional dependency is required beyond your existing cache provider.
 *
 * ## evictAll limitation
 *
 * Spring's [org.springframework.cache.Cache] interface only supports key-level eviction. [evictAll]
 * logs a `WARN` and is a no-op on this tier; stale entries expire via the configured TTL. This is
 * safe because token validation (BCrypt comparison and expiry check) still runs on every request
 * regardless of cache state.
 *
 * Consumers who require immediate user-wide eviction (e.g., forced logout across all devices)
 * should provide a custom [OgiriTokenLookupCache] bean (Tier 1) backed by a `RedisTemplate`
 * SCAN/UNLINK or equivalent pattern-based eviction.
 *
 * ## Recommended configuration
 *
 * ```yaml
 * spring:
 *   cache:
 *     type: redis
 *     cache-names: ogiri-token-lookup
 *     redis:
 *       time-to-live: 300000   # 5 minutes
 *
 * ogiri:
 *   cache:
 *     use-spring-cache-manager: true
 *     cache-name: ogiri-token-lookup
 * ```
 *
 * @param cacheManager The Spring [CacheManager] to wrap.
 * @param cacheName The name of the cache to use; must be listed in `spring.cache.cache-names`.
 */
class OgiriSpringCacheAdapter<T : OgiriToken>(
    cacheManager: CacheManager,
    private val cacheName: String,
) : OgiriTokenLookupCache<T> {

  private val cache: Cache =
      cacheManager.getCache(cacheName)
          ?: throw IllegalStateException(
              "Cache '$cacheName' not found in CacheManager. " +
                  "Ensure spring.cache.cache-names includes '$cacheName' " +
                  "or set ogiri.cache.cache-name to a cache that exists.")

  override fun get(userId: Long, client: String): T? =
      @Suppress("UNCHECKED_CAST") cache.get(OgiriCacheKey.key(userId, client))?.get() as? T

  override fun put(userId: Long, client: String, token: T) =
      cache.put(OgiriCacheKey.key(userId, client), token)

  override fun evict(userId: Long, client: String) {
    cache.evict(OgiriCacheKey.key(userId, client))
  }

  override fun evictAll(userId: Long) {
    logger.warn(
        "evictAll(userId={}) is not supported on the Spring CacheManager tier. " +
            "Stale entries will expire via the configured TTL. " +
            "To support immediate user-wide eviction, provide a custom OgiriTokenLookupCache bean.",
        userId)
  }

  companion object {
    private val logger = LoggerFactory.getLogger(OgiriSpringCacheAdapter::class.java)
  }
}
