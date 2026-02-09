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

import java.lang.reflect.Modifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional

class OgiriTokenServiceOpenMethodsTest {

  @Test
  fun `all @Transactional methods must be non-final for CGLIB proxy interception`() {
    // Group @Transactional methods by name. @JvmOverloads generates bridge methods
    // (fewer params) that are marked final but delegate to the primary method.
    // CGLIB only needs the primary (most-params) variant to be non-final.
    val transactionalByName =
        OgiriTokenService::class
            .java
            .declaredMethods
            .filter { it.isAnnotationPresent(Transactional::class.java) }
            .filter { !it.isSynthetic }
            .groupBy { it.name }

    val allFinalMethods =
        transactionalByName
            .filter { (_, methods) -> methods.all { Modifier.isFinal(it.modifiers) } }
            .keys
            .toList()

    assertFalse(allFinalMethods.isNotEmpty()) {
      "The following @Transactional methods have ALL variants final and will be silently " +
          "skipped by CGLIB proxies: $allFinalMethods. " +
          "Ensure @OgiriService (or another all-open trigger) is applied to the class."
    }
  }

  @Test
  fun `OgiriTokenService class itself must be non-final`() {
    assertFalse(Modifier.isFinal(OgiriTokenService::class.java.modifiers)) {
      "OgiriTokenService must be non-final (open) for CGLIB proxying"
    }
  }
}
