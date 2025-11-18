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
import com.quantipixels.ogiri.security.routes.RouteCatalog
import com.quantipixels.ogiri.security.routes.RouteRegistry
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import com.quantipixels.ogiri.security.tokens.DefaultSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.SubTokenRegistration
import com.quantipixels.ogiri.security.tokens.SubTokenRegistry
import com.quantipixels.ogiri.security.tokens.Token
import com.quantipixels.ogiri.security.tokens.TokenCleanupJob
import com.quantipixels.ogiri.security.tokens.TokenRepository
import com.quantipixels.ogiri.security.tokens.TokenService
import com.quantipixels.ogiri.security.web.OgiriAuthenticationEntryPoint
import com.quantipixels.ogiri.security.web.OgiriTokenAuthenticationFilter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/** Auto-configures ogiri beans (token service, filters, registries). */
@Configuration
@Import(TokenCleanupJob::class)
class OgiriSecurityAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  fun identifierPolicy(): IdentifierPolicy = DefaultIdentifierPolicy()

  @Bean
  @ConditionalOnMissingBean
  fun routeCatalog(registries: ObjectProvider<RouteRegistry>): RouteCatalog =
    RouteCatalog(registries.orderedStream().toList())

  @Bean
  @ConditionalOnMissingBean
  fun authenticationBypassDecider(routeCatalog: RouteCatalog): AuthenticationBypassDecider =
    AuthenticationBypassDecider(routeCatalog)

  @Bean
  @ConditionalOnMissingBean
  fun subTokenRegistry(registrations: ObjectProvider<SubTokenRegistration>): SubTokenRegistry =
    DefaultSubTokenRegistry(registrations.orderedStream().toList())

  @Bean
  @ConditionalOnMissingBean
  fun ogiriTokenService(
    repository: TokenRepository<Token>,
    passwordEncoder: PasswordEncoder,
    tokenUserDirectory: TokenUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: SubTokenRegistry,
  ): TokenService<Token> =
    TokenService(
      repository,
      passwordEncoder,
      tokenUserDirectory,
      identifierPolicy,
      subTokenRegistry,
    )

  @Bean
  @ConditionalOnMissingBean
  fun ogiriAuthenticationEntryPoint(messageSource: MessageSource): AuthenticationEntryPoint =
    OgiriAuthenticationEntryPoint(messageSource)

  @Bean
  @ConditionalOnMissingBean
  fun ogiriTokenAuthenticationFilter(
    tokenUserDirectory: TokenUserDirectory,
    tokenService: TokenService<Token>,
    authenticationEntryPoint: AuthenticationEntryPoint,
    authenticationBypassDecider: AuthenticationBypassDecider,
    identifierPolicy: IdentifierPolicy,
  ): OgiriTokenAuthenticationFilter =
    OgiriTokenAuthenticationFilter(
      tokenUserDirectory,
      tokenService,
      authenticationEntryPoint,
      authenticationBypassDecider,
      identifierPolicy,
    )

  @Bean(name = ["ogiriSecurityFilterChain"])
  @ConditionalOnMissingBean(name = ["ogiriSecurityFilterChain"])
  @ConditionalOnProperty(
    prefix = "ogiri.security",
    name = ["register-filter"],
    havingValue = "true",
    matchIfMissing = true,
  )
  fun ogiriSecurityFilterChain(
    http: HttpSecurity,
    ogiriTokenAuthenticationFilter: OgiriTokenAuthenticationFilter,
    authenticationEntryPoint: AuthenticationEntryPoint,
  ): SecurityFilterChain =
    http
      .csrf { it.disable() }
      .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
      .addFilterBefore(ogiriTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
      .build()
}
