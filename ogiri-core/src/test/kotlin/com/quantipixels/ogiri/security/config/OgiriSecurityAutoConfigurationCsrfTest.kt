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

import com.quantipixels.ogiri.security.core.DefaultIdentifierPolicy
import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.StaticMessageSource
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CsrfFilter

class OgiriSecurityAutoConfigurationCsrfTest {
  private val contextRunner =
      WebApplicationContextRunner()
          .withUserConfiguration(BaseDeps::class.java)
          .withConfiguration(
              AutoConfigurations.of(
                  SecurityAutoConfiguration::class.java,
                  SecurityFilterAutoConfiguration::class.java,
                  UserDetailsServiceAutoConfiguration::class.java,
                  OgiriSecurityAutoConfiguration::class.java,
              ))
          .withPropertyValues("ogiri.cleanup.enabled=false")

  @Test
  fun `auto mode enables csrf when cookies enabled and sameSite none`() {
    contextRunner
        .withPropertyValues(
            "ogiri.security.csrf.enabled=auto",
            "ogiri.cookies.enabled=true",
            "ogiri.cookies.same-site=None",
        )
        .run { context ->
          assertTrue(context.containsBean("ogiriSecurityFilterChain"))
          assertTrue(hasCsrfFilter(context))
        }
  }

  @Test
  fun `auto mode disables csrf when sameSite is strict`() {
    contextRunner
        .withPropertyValues(
            "ogiri.security.csrf.enabled=auto",
            "ogiri.cookies.enabled=true",
            "ogiri.cookies.same-site=Strict",
        )
        .run { context ->
          assertTrue(context.containsBean("ogiriSecurityFilterChain"))
          assertFalse(hasCsrfFilter(context))
        }
  }

  @Test
  fun `explicit true enables csrf regardless of cookie settings`() {
    contextRunner
        .withPropertyValues(
            "ogiri.security.csrf.enabled=true",
            "ogiri.cookies.enabled=false",
            "ogiri.cookies.same-site=Strict",
        )
        .run { context ->
          assertTrue(context.containsBean("ogiriSecurityFilterChain"))
          assertTrue(hasCsrfFilter(context))
        }
  }

  @Test
  fun `explicit false disables csrf even when sameSite none`() {
    contextRunner
        .withPropertyValues(
            "ogiri.security.csrf.enabled=false",
            "ogiri.cookies.enabled=true",
            "ogiri.cookies.same-site=None",
        )
        .run { context ->
          assertTrue(context.containsBean("ogiriSecurityFilterChain"))
          assertFalse(hasCsrfFilter(context))
        }
  }

  private fun hasCsrfFilter(context: AssertableWebApplicationContext): Boolean {
    val chain = context.getBean("ogiriSecurityFilterChain", SecurityFilterChain::class.java)
    val filters = (chain as DefaultSecurityFilterChain).filters
    return filters.any { it is CsrfFilter }
  }

  @Configuration
  class BaseDeps {
    @Bean fun messageSource(): MessageSource = StaticMessageSource()

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        object : PasswordEncoder {
          override fun encode(rawPassword: CharSequence): String = rawPassword.toString()

          override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
              rawPassword.toString() == encodedPassword
        }

    @Bean fun identifierPolicy() = DefaultIdentifierPolicy()

    @Bean fun tokenRepository(): OgiriTokenRepository<TestToken> = InMemoryTokenRepository()

    @Bean
    fun userDirectory(): OgiriUserDirectory =
        object : OgiriUserDirectory {
          override fun loadUserByUsername(username: String): OgiriUser {
            throw UnsupportedOperationException()
          }

          override fun findById(id: Long): OgiriUser? = null

          override fun findByEmail(email: String): OgiriUser? = null

          override fun findByUsername(username: String): OgiriUser? = null

          override fun recordSuccessfulLogin(userId: Long) {}
        }
  }
}
