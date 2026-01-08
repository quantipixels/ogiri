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

import com.quantipixels.ogiri.security.routes.OgiriRouteRegistry
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException

class OgiriMissingBeanFailureAnalyzerTest {

  private val analyzer = OgiriMissingBeanFailureAnalyzer()

  @Test
  fun `should analyze missing OgiriTokenRepository`() {
    val exception = NoSuchBeanDefinitionException(OgiriTokenRepository::class.java)

    val analysis = analyzer.analyze(exception)

    assertNotNull(analysis)
    assertEquals("No OgiriTokenRepository bean found.", analysis!!.description)
    assertTrue(analysis.action.contains("ogiri-jpa"))
    assertTrue(analysis.action.contains("OgiriBaseTokenEntity"))
    assertTrue(analysis.action.contains("JpaRepository"))
  }

  @Test
  fun `should analyze missing OgiriUserDirectory`() {
    val exception = NoSuchBeanDefinitionException(OgiriUserDirectory::class.java)

    val analysis = analyzer.analyze(exception)

    assertNotNull(analysis)
    assertEquals("No OgiriUserDirectory bean found.", analysis!!.description)
    assertTrue(analysis.action.contains("OgiriUserDirectory"))
    assertTrue(analysis.action.contains("findById"))
    assertTrue(analysis.action.contains("findByUsername"))
    assertTrue(analysis.action.contains("OgiriUser"))
  }

  @Test
  fun `should analyze missing OgiriRouteRegistry`() {
    val exception = NoSuchBeanDefinitionException(OgiriRouteRegistry::class.java)

    val analysis = analyzer.analyze(exception)

    assertNotNull(analysis)
    assertEquals("No OgiriRouteRegistry bean found.", analysis!!.description)
    assertTrue(analysis.action.contains("OgiriRouteRegistry"))
    assertTrue(analysis.action.contains("OgiriRoute"))
    assertTrue(analysis.action.contains("/api/auth/login"))
  }

  @Test
  fun `should return null for unrelated bean types`() {
    val exception = NoSuchBeanDefinitionException(String::class.java)

    val analysis = analyzer.analyze(exception)

    assertNull(analysis)
  }

  @Test
  fun `should return null when beanType is null`() {
    // Create exception without bean type info
    val exception = NoSuchBeanDefinitionException("someBean")

    val analysis = analyzer.analyze(exception)

    assertNull(analysis)
  }

  @Test
  fun `should include cause in analysis`() {
    val exception = NoSuchBeanDefinitionException(OgiriTokenRepository::class.java)

    val analysis = analyzer.analyze(exception)

    assertNotNull(analysis)
    assertEquals(exception, analysis!!.cause)
  }
}
