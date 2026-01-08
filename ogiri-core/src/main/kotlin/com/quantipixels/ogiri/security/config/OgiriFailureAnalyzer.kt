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
 * Provides helpful error messages when required Ogiri beans are missing.
 *
 * This failure analyzer intercepts NoSuchBeanDefinitionException errors during application startup
 * and provides actionable guidance for missing OgiriTokenRepository and OgiriUserDirectory beans.
 *
 * Instead of showing a confusing stack trace, users see:
 * - Clear description of what's missing
 * - Recommended solutions with code examples
 * - Links to relevant documentation
 *
 * Example output:
 * ```
 * ***************************
 * APPLICATION FAILED TO START
 * ***************************
 *
 * Description:
 * No OgiriTokenRepository bean found.
 *
 * Action:
 * Ogiri requires an OgiriTokenRepository<T> bean for token persistence.
 *
 * Option 1 - Use ogiri-jpa (recommended for JPA/Hibernate):
 *   Add dependency: implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")
 *   Then extend AbstractJpaTokenRepositoryAdapter
 *
 * Option 2 - Implement manually:
 *   @Repository
 *   class MyTokenRepository : OgiriTokenRepository<MyToken> { ... }
 *
 * Documentation: https://mosobande.github.io/ogiri/guides/database-integration/
 * ```
 */
class OgiriFailureAnalyzer : AbstractFailureAnalyzer<NoSuchBeanDefinitionException>() {

  /**
   * Analyze the exception and provide helpful guidance if it's related to missing Ogiri beans.
   *
   * @param rootFailure The root cause throwable
   * @param cause The NoSuchBeanDefinitionException that was thrown
   * @return FailureAnalysis with description and recommended actions, or null if not Ogiri-related
   */
  override fun analyze(rootFailure: Throwable, cause: NoSuchBeanDefinitionException): FailureAnalysis? {
    val beanType = cause.beanType?.simpleName ?: return null

    return when {
      beanType.contains("OgiriTokenRepository") -> createTokenRepositoryAnalysis(cause)
      beanType.contains("OgiriUserDirectory") -> createUserDirectoryAnalysis(cause)
      else -> null
    }
  }

  /**
   * Create failure analysis for missing OgiriTokenRepository bean.
   *
   * Provides two options:
   * 1. Use ogiri-jpa module (recommended for JPA users)
   * 2. Implement OgiriTokenRepository manually
   *
   * @param cause The original exception
   * @return FailureAnalysis with actionable guidance
   */
  private fun createTokenRepositoryAnalysis(cause: NoSuchBeanDefinitionException): FailureAnalysis {
    val description = "No OgiriTokenRepository bean found."

    val action = """
      |Ogiri requires an OgiriTokenRepository<T> bean for token persistence.
      |
      |Option 1 - Use ogiri-jpa (recommended for JPA/Hibernate):
      |  Add dependency: implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")
      |  Then extend AbstractJpaTokenRepositoryAdapter
      |
      |  Example:
      |    @Repository
      |    class TokenRepositoryAdapter(jpa: TokenJpaRepository) :
      |        AbstractJpaTokenRepositoryAdapter<Token, TokenJpaRepository>(jpa) {
      |      // Implement required abstract methods
      |    }
      |
      |Option 2 - Implement manually:
      |  @Repository
      |  class MyTokenRepository : OgiriTokenRepository<MyToken> {
      |    override fun save(token: MyToken): MyToken = ...
      |    override fun findById(id: Long): MyToken? = ...
      |    // ... implement all required methods
      |  }
      |
      |Documentation: https://mosobande.github.io/ogiri/guides/database-integration/
      """.trimMargin()

    return FailureAnalysis(description, action, cause)
  }

  /**
   * Create failure analysis for missing OgiriUserDirectory bean.
   *
   * Provides guidance on implementing the required user directory interface.
   *
   * @param cause The original exception
   * @return FailureAnalysis with actionable guidance
   */
  private fun createUserDirectoryAnalysis(cause: NoSuchBeanDefinitionException): FailureAnalysis {
    val description = "No OgiriUserDirectory bean found."

    val action = """
      |Ogiri requires an OgiriUserDirectory bean to resolve users.
      |
      |Create a component that implements OgiriUserDirectory:
      |
      |  @Component
      |  class MyUserDirectory(
      |      private val userRepository: UserRepository
      |  ) : OgiriUserDirectory {
      |
      |      override fun findById(id: Long): OgiriUser? =
      |          userRepository.findById(id).orElse(null)
      |
      |      override fun loadUserByUsername(username: String): OgiriUser =
      |          userRepository.findByUsername(username)
      |              ?: throw UsernameNotFoundException("User not found: ${'$'}username")
      |  }
      |
      |Your User entity must implement the OgiriUser interface.
      |
      |Documentation: https://mosobande.github.io/ogiri/quickstart/
      """.trimMargin()

    return FailureAnalysis(description, action, cause)
  }
}
