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
package com.quantipixels.ogiri.security.config

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class OgiriPasswordEncoderAutoConfigurationTest {

  private val autoConfiguration = OgiriSecurityAutoConfiguration()

  @Test
  fun `ogiriPasswordEncoder provides bcrypt implementation`() {
    val encoder = autoConfiguration.ogiriPasswordEncoder()
    val rawPassword = "mySecurePassword123"
    val encoded = encoder.encode(rawPassword)

    assertTrue(encoder is BCryptPasswordEncoder)
    assertTrue(encoded.startsWith("\$2"))
    assertTrue(encoder.matches(rawPassword, encoded))
  }

  @Test
  fun `ogiriPasswordEncoder is guarded with ConditionalOnMissingBean`() {
    val method = OgiriSecurityAutoConfiguration::class.java.getMethod("ogiriPasswordEncoder")
    val condition = method.getAnnotation(ConditionalOnMissingBean::class.java)

    assertNotNull(condition)
  }
}
