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
package com.quantipixels.ogiri.jpa

import com.quantipixels.ogiri.security.config.OgiriSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Auto-configuration for Ogiri JPA adapter module.
 *
 * This configuration activates when Spring Data JPA is on the classpath, indicating that the
 * application is using JPA for persistence. It runs after OgiriSecurityAutoConfiguration to ensure
 * core Ogiri beans are available.
 *
 * The ogiri-jpa module provides:
 * - [OgiriBaseTokenEntity]: A @MappedSuperclass with all token fields and JPA annotations
 * - [AbstractJpaTokenRepositoryAdapter]: Base adapter reducing JPA repository boilerplate
 *
 * Users can extend these classes to quickly integrate Ogiri with JPA:
 *
 * Example:
 * ```kotlin
 * // 1. Extend the base entity
 * @Entity
 * @Table(name = "tokens")
 * class Token : OgiriBaseTokenEntity()
 *
 * // 2. Create JPA repository
 * @Repository
 * interface TokenJpaRepository : JpaRepository<Token, Long> {
 *   fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<Token>
 *   // ... other custom queries
 * }
 *
 * // 3. Create adapter extending AbstractJpaTokenRepositoryAdapter
 * @Repository
 * class TokenRepositoryAdapter(jpa: TokenJpaRepository) :
 *     AbstractJpaTokenRepositoryAdapter<Token, TokenJpaRepository>(jpa) {
 *   // Implement abstract methods by delegating to JPA repository
 * }
 * ```
 *
 * This reduces integration code from ~100 lines to ~20 lines.
 *
 * Dependency configuration:
 * ```kotlin
 * dependencies {
 *   implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")
 *   // Transitively includes ogiri-core and spring-boot-starter-data-jpa
 * }
 * ```
 */
@AutoConfiguration(after = [OgiriSecurityAutoConfiguration::class])
@ConditionalOnClass(JpaRepository::class)
class OgiriJpaAutoConfiguration {
  // This configuration class is currently marker-only.
  // Future enhancements may include:
  // - Automatic detection of entities extending OgiriBaseTokenEntity
  // - Validation of JPA repository implementations
  // - Default transaction management configurations
  // - JPA-specific health indicators or metrics
}
