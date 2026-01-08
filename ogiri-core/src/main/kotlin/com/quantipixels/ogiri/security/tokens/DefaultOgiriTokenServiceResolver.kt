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
package com.quantipixels.ogiri.security.tokens

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory

class DefaultOgiriTokenServiceResolver(
    private val tokenServices: Map<String, OgiriTokenService<*>>,
    private val properties: OgiriConfigurationProperties,
    private val beanFactory: ConfigurableListableBeanFactory,
) : OgiriTokenServiceResolver {
  /**
   * Resolve the single OgiriTokenService bean to use for token operations.
   *
   * Selects and returns one OgiriTokenService from the registered beans, preferring a single
   * registered bean or a single bean marked `@Primary`. If no service is available or multiple
   * candidates exist without a single primary, resolution fails.
   *
   * @return The selected OgiriTokenService instance.
   * @throws IllegalStateException if no OgiriTokenService beans are found or if multiple beans are
   *   found without a single `@Primary` candidate.
   */
  override fun resolve(): OgiriTokenService<*> {
    if (tokenServices.isEmpty()) {
      val tokenServiceIsEnabled = properties.auth.registerTokenService
      if (tokenServiceIsEnabled) {
        error(
            "No OgiriTokenService bean found despite ogiri.auth.register-token-service=true. " +
                "Ensure a valid OgiriTokenRepository bean exists, or provide a custom OgiriTokenService implementation.")
      }
      error(
          "No OgiriTokenService bean found. Provide a custom OgiriTokenService (default creation disabled via ogiri.auth.register-token-service=false).")
    }

    if (tokenServices.size == 1) {
      return tokenServices.values.first()
    }

    val primaryBeanNames =
        tokenServices.keys
            .filter { beanName ->
              beanFactory.containsBeanDefinition(beanName) &&
                  beanFactory.getBeanDefinition(beanName).isPrimary
            }
            .sorted()
    if (primaryBeanNames.size == 1) {
      return tokenServices.getValue(primaryBeanNames.single())
    }

    error(
        "Multiple OgiriTokenService beans found (${tokenServices.keys.sorted().joinToString()}): mark one @Primary or inject by @Qualifier.")
  }
}
