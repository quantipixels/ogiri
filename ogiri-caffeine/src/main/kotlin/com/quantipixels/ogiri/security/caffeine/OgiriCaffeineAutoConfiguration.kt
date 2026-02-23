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
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.tokens.OgiriToken
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/**
 * Autoconfiguration for the Caffeine-backed [OgiriTokenLookupCache].
 *
 * Activates only when **all** of the following are true:
 * - `com.github.benmanes.caffeine.cache.Cache` is on the classpath (`ogiri-caffeine` dependency
 *   added)
 * - `ogiri.lookup.type=caffeine` is set in `application.yml` (explicit opt-in)
 * - No `OgiriTokenLookupCache` bean is already registered (custom bean wins)
 */
@AutoConfiguration
@ConditionalOnClass(Cache::class)
@ConditionalOnMissingBean(OgiriTokenLookupCache::class)
@ConditionalOnProperty(
    prefix = "ogiri.lookup",
    name = ["type"],
    havingValue = "caffeine",
    matchIfMissing = false,
)
class OgiriCaffeineAutoConfiguration {

  @Bean
  fun <T : OgiriToken> ogiriCaffeineTokenLookupCache(
      properties: OgiriConfigurationProperties
  ): OgiriTokenLookupCache<T> = CaffeineOgiriTokenLookupCache(properties)
}
