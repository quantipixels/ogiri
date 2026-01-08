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
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.helpers.AuthenticationBypassDecider
import com.quantipixels.ogiri.security.routes.OgiriRouteCatalog
import com.quantipixels.ogiri.security.routes.OgiriRouteRegistry
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.DefaultOgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.DefaultOgiriTokenServiceResolver
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistration
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.OgiriToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenCleanupJob
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenServiceResolver
import com.quantipixels.ogiri.security.web.OgiriAuthenticationEntryPoint
import com.quantipixels.ogiri.security.web.OgiriTokenAuthenticationFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Auto-configures ogiri beans (token service, filters, registries).
 *
 * This configuration class:
 * - Enables the OgiriConfigurationProperties for property binding
 * - Creates core ogiri beans (TokenService, TokenAuthenticationFilter, etc.)
 * - Registers the security filter chain when enabled via properties
 * - Conditionally registers OgiriTokenCleanupJob for expired token deletion
 *
 * Configuration is loaded from application.yml/application.properties with prefix "ogiri". All
 * beans are registered with @ConditionalOnMissingBean for easy override.
 */
@Configuration
@EnableConfigurationProperties(OgiriConfigurationProperties::class)
class OgiriSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(DefaultIdentifierPolicy::class)
  fun identifierPolicy(): DefaultIdentifierPolicy = DefaultIdentifierPolicy()

  @Bean
  @ConditionalOnMissingBean(OgiriRouteCatalog::class)
  fun routeCatalog(registries: ObjectProvider<OgiriRouteRegistry>): OgiriRouteCatalog =
      OgiriRouteCatalog(registries.orderedStream().toList())

  @Bean
  @ConditionalOnMissingBean(AuthenticationBypassDecider::class)
  fun authenticationBypassDecider(routeCatalog: OgiriRouteCatalog): AuthenticationBypassDecider =
      AuthenticationBypassDecider(routeCatalog)

  @Bean
  @ConditionalOnMissingBean(DefaultOgiriSubTokenRegistry::class)
  fun subTokenRegistry(
      registrations: ObjectProvider<OgiriSubTokenRegistration>
  ): DefaultOgiriSubTokenRegistry =
      DefaultOgiriSubTokenRegistry(registrations.orderedStream().toList())

  @Bean
  @ConditionalOnMissingBean(OgiriTokenService::class)
  @ConditionalOnProperty(
      prefix = "ogiri.auth",
      name = ["register-token-service"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun ogiriTokenService(
      repository: OgiriTokenRepository<*>,
      passwordEncoder: PasswordEncoder,
      ogiriUserDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
  ): OgiriTokenService<*> =
      @Suppress("UNCHECKED_CAST")
      OgiriTokenService(
          repository as OgiriTokenRepository<OgiriToken>,
          passwordEncoder,
          ogiriUserDirectory,
          identifierPolicy,
          subTokenRegistry,
          properties,
      )
          as OgiriTokenService<*>

  @Bean
  @ConditionalOnMissingBean(DefaultOgiriTokenServiceResolver::class)
  fun ogiriTokenServiceResolver(
      tokenServices: Map<String, OgiriTokenService<*>>,
      properties: OgiriConfigurationProperties,
      beanFactory: ConfigurableListableBeanFactory,
  ): DefaultOgiriTokenServiceResolver =
      DefaultOgiriTokenServiceResolver(tokenServices, properties, beanFactory)

  @Bean
  @ConditionalOnMissingBean(OgiriAuthenticationEntryPoint::class)
  fun ogiriAuthenticationEntryPoint(messageSource: MessageSource): OgiriAuthenticationEntryPoint =
      OgiriAuthenticationEntryPoint(messageSource)

  @Bean
  @ConditionalOnMissingBean(OgiriTokenAuthenticationFilter::class)
  fun ogiriTokenAuthenticationFilter(
      ogiriUserDirectory: OgiriUserDirectory,
      tokenServiceResolver: OgiriTokenServiceResolver,
      authenticationEntryPoint: AuthenticationEntryPoint,
      authenticationBypassDecider: AuthenticationBypassDecider,
      identifierPolicy: IdentifierPolicy,
      properties: OgiriConfigurationProperties,
  ): OgiriTokenAuthenticationFilter =
      OgiriTokenAuthenticationFilter(
          ogiriUserDirectory,
          tokenServiceResolver.resolve(),
          authenticationEntryPoint,
          authenticationBypassDecider,
          identifierPolicy,
          properties,
      )

  @Bean
  @ConditionalOnMissingBean(OgiriTokenCleanupJob::class)
  @ConditionalOnProperty(
      prefix = "ogiri.cleanup",
      name = ["enabled"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun ogiriTokenCleanupJob(tokenServiceResolver: OgiriTokenServiceResolver): OgiriTokenCleanupJob =
      OgiriTokenCleanupJob(tokenServiceResolver)

  @Bean(name = ["ogiriSecurityFilterChain"])
  @ConditionalOnMissingBean(name = ["ogiriSecurityFilterChain"])
  @ConditionalOnProperty(
      prefix = "ogiri.security",
      name = ["register-filter"],
      havingValue = "true",
      matchIfMissing = true,
  )
  @ConditionalOnWebApplication
  fun ogiriSecurityFilterChain(
      http: HttpSecurity,
      ogiriTokenAuthenticationFilter: OgiriTokenAuthenticationFilter,
      authenticationEntryPoint: AuthenticationEntryPoint,
  ): SecurityFilterChain =
      http
          .csrf { it.disable() }
          .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
          .addFilterBefore(
              ogiriTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
          .build()
}
