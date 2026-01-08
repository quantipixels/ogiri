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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class OgiriPasswordEncoderAutoConfigurationTest {

  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  PasswordEncoderTestConfig::class.java))

  @Test
  fun `should auto-configure BCryptPasswordEncoder when no PasswordEncoder bean exists`() {
    contextRunner.run { context ->
      assertTrue(context.containsBean("ogiriPasswordEncoder"))
      val encoder = context.getBean(PasswordEncoder::class.java)
      assertInstanceOf(BCryptPasswordEncoder::class.java, encoder)
    }
  }

  @Test
  fun `should not create auto-configured encoder when user provides one`() {
    contextRunner.withUserConfiguration(CustomPasswordEncoderConfig::class.java).run { context ->
      val encoders = context.getBeansOfType(PasswordEncoder::class.java)
      assertEquals(1, encoders.size)
      assertTrue(encoders.containsKey("customPasswordEncoder"))

      // Verify it's our custom encoder, not BCrypt
      val encoder = context.getBean(PasswordEncoder::class.java)
      assertEquals("test-encoded", encoder.encode("test"))
    }
  }

  @Test
  fun `auto-configured BCryptPasswordEncoder should hash passwords correctly`() {
    contextRunner.run { context ->
      val encoder = context.getBean(PasswordEncoder::class.java)
      val rawPassword = "mySecurePassword123"
      val encoded = encoder.encode(rawPassword)

      // Verify it's a BCrypt hash (starts with $2)
      assertTrue(encoded.startsWith("\$2"))
      assertTrue(encoder.matches(rawPassword, encoded))
    }
  }

  /** Test configuration that provides the password encoder bean with conditional logic. */
  @Configuration
  class PasswordEncoderTestConfig {
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder::class)
    fun ogiriPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
  }

  @Configuration
  class CustomPasswordEncoderConfig {
    @Bean
    fun customPasswordEncoder(): PasswordEncoder =
        object : PasswordEncoder {
          override fun encode(rawPassword: CharSequence): String = "test-encoded"

          override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
              encodedPassword == "test-encoded"
        }
  }
}
