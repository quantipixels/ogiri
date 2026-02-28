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

/**
 * Cache key helpers shared by all [OgiriTokenLookupCache] implementations.
 *
 * All implementations (Caffeine, Redis, Spring CacheManager) must use the same key format to ensure
 * consistency in eviction logic and observability tooling. The namespaced prefix (`ogiri:token:`)
 * prevents collisions when the backing store is shared with other tenants.
 *
 * **Key format:** `ogiri:token:{userId}:{client}`
 */
object OgiriCacheKey {
  fun key(userId: Long, client: String) = "ogiri:token:$userId:$client"

  fun prefix(userId: Long) = "ogiri:token:$userId:"
}
