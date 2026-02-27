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
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionMessage
import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import org.springframework.data.redis.connection.RedisConnectionFactory

/**
 * Autoconfiguration for the Redis-backed [OgiriTokenLookupCache].
 *
 * Activates only when **all** of the following are true:
 * - `spring-boot-starter-data-redis` is on the classpath
 * - `ogiri.lookup.type=redis` is set in `application.yml` (case-insensitive, explicit opt-in)
 * - No `OgiriTokenLookupCache` bean is already registered (custom bean wins)
 *
 * The bean uses the application's existing [RedisConnectionFactory] — no extra Redis configuration
 * is needed beyond the standard `spring.data.redis.*` properties.
 */
@AutoConfiguration
@ConditionalOnClass(RedisConnectionFactory::class)
@ConditionalOnMissingBean(OgiriTokenLookupCache::class)
@Conditional(OgiriRedisAutoConfiguration.OnRedisType::class)
class OgiriRedisAutoConfiguration {

  @Bean
  fun <T : OgiriToken> ogiriRedisTokenLookupCache(
      connectionFactory: RedisConnectionFactory,
      properties: OgiriConfigurationProperties,
  ): OgiriTokenLookupCache<T> = RedisOgiriTokenLookupCache(connectionFactory, properties)

  internal class OnRedisType : SpringBootCondition() {
    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata,
    ): ConditionOutcome {
      val normalized =
          context.environment.getProperty("ogiri.lookup.type")?.trim()?.lowercase() ?: ""
      return if (normalized == "redis") {
        ConditionOutcome(
            true,
            ConditionMessage.forCondition("OgiriRedisType")
                .found("property")
                .items("ogiri.lookup.type=redis"),
        )
      } else {
        ConditionOutcome(
            false,
            ConditionMessage.forCondition("OgiriRedisType")
                .didNotFind("property with value 'redis'")
                .items("ogiri.lookup.type"),
        )
      }
    }
  }
}
