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

import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis

/**
 * Failure analyzer that provides helpful error messages when required Ogiri beans are missing.
 *
 * Instead of cryptic Spring injection failures, users see actionable guidance on how to configure
 * the missing beans.
 */
class OgiriMissingBeanFailureAnalyzer : AbstractFailureAnalyzer<NoSuchBeanDefinitionException>() {

  override fun analyze(
      rootFailure: Throwable,
      cause: NoSuchBeanDefinitionException,
  ): FailureAnalysis? {
    val beanType = cause.beanType?.simpleName?.takeIf { it.isNotBlank() } ?: return null

    return when {
      beanType.contains("OgiriTokenRepository") ->
          FailureAnalysis(
              "No OgiriTokenRepository bean found.",
              """
                |Ogiri requires an OgiriTokenRepository<T> bean for token persistence.
                |
                |Option 1 - Use ogiri-jpa (recommended for JPA/Hibernate):
                |  Add dependency:
                |    implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")
                |
                |  Then create your token entity and repository:
                |    @Entity
                |    class MyToken : OgiriBaseTokenEntity()
                |
                |    @Repository
                |    interface MyTokenRepository :
                |        JpaRepository<MyToken, Long>,
                |        OgiriTokenRepository<MyToken>
                |
                |  Spring Data automatically generates all query implementations.
                |
                |Option 2 - Implement manually:
                |  @Repository
                |  class MyTokenRepository : OgiriTokenRepository<MyToken> {
                |      override fun save(token: MyToken): MyToken = ...
                |      override fun findById(id: Long): MyToken? = ...
                |      // ... implement all required methods
                |  }
                |
                |Documentation: https://quantipixels.github.io/ogiri/database/
              """
                  .trimMargin(),
              cause,
          )
      beanType.contains("OgiriUserDirectory") ->
          FailureAnalysis(
              "No OgiriUserDirectory bean found.",
              """
                |Ogiri requires an OgiriUserDirectory bean to resolve users.
                |
                |Create a component that implements OgiriUserDirectory:
                |
                |  @Component
                |  class MyUserDirectory(private val userService: UserService) : OgiriUserDirectory {
                |
                |      override fun findById(id: Long): OgiriUser? =
                |          userService.findById(id)
                |
                |      override fun findByUsername(username: String): OgiriUser? =
                |          userService.findByUsername(username)
                |
                |      override fun findByEmail(email: String): OgiriUser? =
                |          userService.findByEmail(email)
                |
                |      override fun loadUserByUsername(username: String): OgiriUser =
                |          findByUsername(username)
                |              ?: throw UsernameNotFoundException("User not found: ${'$'}username")
                |
                |      override fun recordSuccessfulLogin(userId: Long) {
                |          userService.updateLastLogin(userId)
                |      }
                |  }
                |
                |Your user entity must implement OgiriUser:
                |
                |  class User : OgiriUser {
                |      override fun getOgiriUserId(): Long = id
                |      // ... UserDetails methods
                |  }
                |
                |Documentation: https://quantipixels.github.io/ogiri/quickstart/
              """
                  .trimMargin(),
              cause,
          )
      beanType.contains("OgiriRouteRegistry") ->
          FailureAnalysis(
              "No OgiriRouteRegistry bean found.",
              """
                |Ogiri requires at least one OgiriRouteRegistry bean to define public routes.
                |
                |Create a component that implements OgiriRouteRegistry:
                |
                |  @Component
                |  class MyRouteRegistry : OgiriRouteRegistry {
                |      override fun routes(): List<OgiriRoute> = listOf(
                |          OgiriRoute.post("/api/auth/login"),
                |          OgiriRoute.post("/api/auth/register"),
                |          OgiriRoute.get("/api/health"),
                |          OgiriRoute.get("/api/public/**"),
                |      )
                |  }
                |
                |Routes defined here bypass authentication.
                |
                |Documentation: https://quantipixels.github.io/ogiri/configuration/
              """
                  .trimMargin(),
              cause,
          )
      else -> null
    }
  }
}
