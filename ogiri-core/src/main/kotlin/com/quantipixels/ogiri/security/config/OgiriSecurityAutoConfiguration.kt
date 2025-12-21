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
import org.springframework.security.config.http.SessionCreationPolicy
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
  /**
   * Provides a default identifier policy used by the token system.
   *
   * This supplies a DefaultIdentifierPolicy instance when no other IdentifierPolicy bean is
   * present.
   *
   * @return a new DefaultIdentifierPolicy instance
   */
  @Bean
  @ConditionalOnMissingBean(IdentifierPolicy::class)
  fun identifierPolicy(): DefaultIdentifierPolicy = DefaultIdentifierPolicy()

  /**
   * Creates an OgiriRouteCatalog by aggregating all available OgiriRouteRegistry beans in order.
   *
   * @param registries Provider of OgiriRouteRegistry instances; collected in the provider's
   *   declared order.
   * @return An OgiriRouteCatalog containing the ordered list of route registries.
   */
  @Bean
  @ConditionalOnMissingBean(OgiriRouteCatalog::class)
  fun routeCatalog(registries: ObjectProvider<OgiriRouteRegistry>): OgiriRouteCatalog =
      OgiriRouteCatalog(registries.orderedStream().toList())

  /**
   * Creates an AuthenticationBypassDecider that determines which routes should bypass
   * authentication using the provided route catalog.
   *
   * @param routeCatalog The catalog of registered routes used to evaluate bypass rules.
   * @return An AuthenticationBypassDecider configured with the given route catalog.
   */
  @Bean
  @ConditionalOnMissingBean(AuthenticationBypassDecider::class)
  fun authenticationBypassDecider(routeCatalog: OgiriRouteCatalog): AuthenticationBypassDecider =
      AuthenticationBypassDecider(routeCatalog)

  /**
   * Creates a DefaultOgiriSubTokenRegistry initialized with the available sub-token registrations.
   *
   * @param registrations provider of OgiriSubTokenRegistration instances; the provider's ordered
   *   stream is used to preserve registration order
   * @return a DefaultOgiriSubTokenRegistry containing the registrations provided by `registrations`
   */
  @Bean
  @ConditionalOnMissingBean(DefaultOgiriSubTokenRegistry::class)
  fun subTokenRegistry(
      registrations: ObjectProvider<OgiriSubTokenRegistration>
  ): DefaultOgiriSubTokenRegistry =
      DefaultOgiriSubTokenRegistry(registrations.orderedStream().toList())

  /**
   * Creates a default OgiriTokenService configured with the provided collaborators for token
   * management.
   *
   * @param repository Repository used to persist and retrieve tokens.
   * @param passwordEncoder Encoder used to hash or verify token secrets.
   * @param ogiriUserDirectory Directory used to resolve and validate users associated with tokens.
   * @param identifierPolicy Policy responsible for generating token identifiers.
   * @param subTokenRegistry Registry of sub-token registrations used by the service.
   * @param properties Ogiri configuration properties influencing token behavior.
   * @return An instance of OgiriTokenService configured with the given repository, encoder,
   *   directory, identifier policy, sub-token registry, and properties.
   */
  @Bean
  @ConditionalOnMissingBean(OgiriTokenService::class)
  @ConditionalOnProperty(
      prefix = "ogiri.auth",
      name = ["register-token-service"],
      havingValue = "true",
      matchIfMissing = true,
  )
  fun <T : OgiriToken> ogiriTokenService(
      repository: OgiriTokenRepository<T>,
      passwordEncoder: PasswordEncoder,
      ogiriUserDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
  ): OgiriTokenService<T> =
      OgiriTokenService(
          repository,
          passwordEncoder,
          ogiriUserDirectory,
          identifierPolicy,
          subTokenRegistry,
          properties,
      )

  /**
   * Creates a resolver that selects and exposes available OgiriTokenService instances.
   *
   * @param tokenServices Map of all registered `OgiriTokenService` beans keyed by bean name.
   * @param properties Ogiri configuration properties used to influence resolver behavior.
   * @param beanFactory Spring bean factory used for any runtime bean resolution required by the
   *   resolver.
   * @return A `DefaultOgiriTokenServiceResolver` that resolves the appropriate `OgiriTokenService`
   *   implementations.
   */
  @Bean
  @ConditionalOnMissingBean(DefaultOgiriTokenServiceResolver::class)
  fun ogiriTokenServiceResolver(
      tokenServices: Map<String, OgiriTokenService<*>>,
      properties: OgiriConfigurationProperties,
      beanFactory: ConfigurableListableBeanFactory,
  ): DefaultOgiriTokenServiceResolver =
      DefaultOgiriTokenServiceResolver(tokenServices, properties, beanFactory)

  /**
   * Creates an OgiriAuthenticationEntryPoint that produces localized authentication failure
   * responses.
   *
   * @param messageSource source of localized messages used by the entry point
   * @return an OgiriAuthenticationEntryPoint configured with the given MessageSource
   */
  @Bean
  @ConditionalOnMissingBean(OgiriAuthenticationEntryPoint::class)
  fun ogiriAuthenticationEntryPoint(messageSource: MessageSource): OgiriAuthenticationEntryPoint =
      OgiriAuthenticationEntryPoint(messageSource)

  /**
   * Creates an OgiriTokenAuthenticationFilter configured with the resolved token service and
   * provided collaborators.
   *
   * @param ogiriUserDirectory Directory used to resolve users from tokens.
   * @param tokenServiceResolver Resolver used to obtain the active OgiriTokenService instance.
   * @param authenticationEntryPoint Entry point invoked on authentication failures.
   * @param authenticationBypassDecider Decider that determines whether authentication should be
   *   bypassed for a request.
   * @param identifierPolicy Policy used to extract and validate identifiers from requests/tokens.
   * @param properties Ogiri configuration properties influencing filter behavior.
   * @return A configured OgiriTokenAuthenticationFilter instance.
   */
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

  /**
   * Creates a periodic job that cleans up expired Ogiri tokens using the provided token service
   * resolver.
   *
   * @param tokenServiceResolver Resolver used to locate available token services for cleanup.
   * @return The configured [OgiriTokenCleanupJob] responsible for expiring and removing tokens.
   */
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

  /**
   * Configures a stateless SecurityFilterChain that applies Ogiri token authentication.
   *
   * This chain disables CSRF, enforces stateless session management, delegates authentication
   * failures to the provided entry point, and inserts the Ogiri token authentication filter before
   * the UsernamePasswordAuthenticationFilter. Callers must configure authorization rules (for
   * example via authorizeHttpRequests) if route access restrictions are required.
   *
   * @return A SecurityFilterChain that enforces token-based authentication, disables CSRF, uses
   *   stateless sessions, and delegates authentication failures to the provided
   *   AuthenticationEntryPoint.
   */
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
          .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
          .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
          .addFilterBefore(
              ogiriTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
          // NOTE: Callers must configure their own authorizeHttpRequests() rules if needed.
          // By default, this chain only configures token-based authentication and stateless
          // sessions.
          .build()
}
