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

/**
 * Optional SPI for caching token entity lookups.
 *
 * When a bean implementing this interface is present in the application context,
 * [com.quantipixels.ogiri.security.tokens.OgiriTokenService] checks it before hitting the
 * repository on every read. Implementations own their eviction strategy.
 *
 * When no bean is present the service falls through to the repository directly — zero behavior
 * change for existing consumers.
 *
 * ## Key format
 *
 * All implementations must use [OgiriCacheKey] to produce cache keys so that eviction logic and
 * observability tooling are consistent. The canonical key format is
 * `ogiri:token:{userId}:{client}`.
 *
 * ## evictAll contract
 *
 * Implementations backed by a store that does not support pattern-based eviction (e.g., the Spring
 * [org.springframework.cache.CacheManager] tier) may implement [evictAll] as a no-op. In that case
 * stale entries expire via the configured TTL — token validation (BCrypt + expiry check) still runs
 * on every request regardless of cache state, so this is safe for correctness. Document the no-op
 * behaviour in the implementation's KDoc so consumers can make an informed choice.
 *
 * Example (Caffeine):
 * ```kotlin
 * @Component
 * class CaffeineTokenLookupCache : OgiriTokenLookupCache<UserToken> {
 *     private val cache = Caffeine.newBuilder()
 *         .maximumSize(10_000)
 *         .expireAfterWrite(Duration.ofMinutes(5))
 *         .build<String, UserToken>()
 *
 *     override fun get(userId: Long, client: String) =
 *         cache.getIfPresent(OgiriCacheKey.key(userId, client))
 *     override fun put(userId: Long, client: String, token: UserToken) =
 *         cache.put(OgiriCacheKey.key(userId, client), token)
 *     override fun evict(userId: Long, client: String) =
 *         cache.invalidate(OgiriCacheKey.key(userId, client))
 *     override fun evictAll(userId: Long) =
 *         cache.invalidateAll(cache.asMap().keys.filter { it.startsWith(OgiriCacheKey.prefix(userId)) })
 * }
 * ```
 */
interface OgiriTokenLookupCache<T : OgiriToken> {
  /** Return a cached token entity, or null on cache miss. */
  fun get(userId: Long, client: String): T?

  /** Store a token entity in the cache. */
  fun put(userId: Long, client: String, token: T)

  /** Remove the cache entry for a specific user/client combination. */
  fun evict(userId: Long, client: String)

  /**
   * Remove all cache entries for a user (e.g., on full revocation).
   *
   * Implementations that cannot support pattern-based eviction may log a warning and return without
   * removing entries; callers must not rely on immediate eviction from this tier.
   */
  fun evictAll(userId: Long)
}
