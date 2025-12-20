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
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenType
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.StaticMessageSource
import org.springframework.security.crypto.password.PasswordEncoder

class OgiriSecurityAutoConfigurationWiringTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withUserConfiguration(BaseDeps::class.java)
          .withConfiguration(
              org.springframework.boot.autoconfigure.AutoConfigurations.of(
                  OgiriSecurityAutoConfiguration::class.java))
          .withPropertyValues(
              "ogiri.security.register-filter=false",
              "ogiri.cleanup.enabled=false",
          )

  companion object {
    fun createTestTokenService(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        object :
            OgiriTokenService<TestToken>(
                repository,
                passwordEncoder,
                userDirectory,
                identifierPolicy,
                subTokenRegistry,
                properties,
            ) {
          override fun tokenFactory(
              userId: Long,
              client: String,
              hashedToken: String,
              tokenType: OgiriTokenType,
              expiry: Instant,
              tokenSubtype: String?,
              plainTokenValue: String,
          ): TestToken =
              TestToken(
                      userId = userId,
                      client = client,
                      token = hashedToken,
                      tokenType = tokenType.label,
                      expiryAt = expiry,
                      tokenSubtype = tokenSubtype,
                  )
                  .apply { plainToken = plainTokenValue }
        }
  }

  @Test
  fun `should not create default token service when user provides one`() {
    contextRunner.withUserConfiguration(UserProvidedTokenService::class.java).run { context ->
      val tokenServices = context.getBeansOfType(OgiriTokenService::class.java)
      org.junit.jupiter.api.Assertions.assertEquals(1, tokenServices.size)
    }
  }

  @Test
  fun `should resolve primary token service when multiple exist`() {
    contextRunner.withUserConfiguration(TwoTokenServicesOnePrimary::class.java).run { context ->
      org.junit.jupiter.api.Assertions.assertTrue(
          context.containsBean("ogiriTokenAuthenticationFilter"))
    }
  }

  @Test
  fun `should fail with clear message when multiple token services and none primary`() {
    contextRunner.withUserConfiguration(TwoTokenServicesNoPrimary::class.java).run { context ->
      org.junit.jupiter.api.Assertions.assertTrue(context.startupFailure != null)
      org.junit.jupiter.api.Assertions.assertTrue(
          context.startupFailure!!.message!!.contains("Multiple OgiriTokenService beans found"))
    }
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

  @Configuration
  class UserProvidedTokenService {
    @Bean
    fun myTokenService(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        createTestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            properties)
  }

  @Configuration
  class TwoTokenServicesOnePrimary {
    @Bean
    @Primary
    fun tokenServiceA(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        createTestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            properties)

    @Bean
    fun tokenServiceB(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        createTestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            properties)
  }

  @Configuration
  class TwoTokenServicesNoPrimary {
    @Bean
    fun tokenServiceA(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        createTestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            properties)

    @Bean
    fun tokenServiceB(
        repository: OgiriTokenRepository<TestToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: DefaultIdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
    ): OgiriTokenService<TestToken> =
        createTestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            subTokenRegistry,
            properties)
  }
}
