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
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Auto-configuration for Ogiri JPA support.
 *
 * This configuration is automatically loaded when:
 * - Spring Data JPA is on the classpath (JpaRepository class present)
 * - After OgiriSecurityAutoConfiguration has been processed
 *
 * Currently, this configuration serves as a marker for JPA support detection. Users are expected to
 * create their own:
 * - Token entity extending [OgiriBaseTokenEntity]
 * - JPA repository interface
 * - Repository adapter extending [AbstractJpaTokenRepositoryAdapter]
 *
 * Future enhancements may include:
 * - Auto-detection of OgiriBaseTokenEntity subclasses
 * - Automatic repository adapter generation
 * - JPA-specific configuration properties
 */
@Configuration
@ConditionalOnClass(JpaRepository::class)
@AutoConfigureAfter(OgiriSecurityAutoConfiguration::class)
class OgiriJpaAutoConfiguration
