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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OgiriTokenTypeTest {

  @Test
  fun `ofOrDefault should return APP for null input`() {
    assertEquals(OgiriTokenType.APP, OgiriTokenType.ofOrDefault(null))
  }

  @Test
  fun `ofOrDefault should return correct type for valid labels`() {
    assertEquals(OgiriTokenType.APP, OgiriTokenType.ofOrDefault("app"))
    assertEquals(OgiriTokenType.APP, OgiriTokenType.ofOrDefault("APP"))
    assertEquals(OgiriTokenType.SUB, OgiriTokenType.ofOrDefault("sub"))
    assertEquals(OgiriTokenType.SUB, OgiriTokenType.ofOrDefault("SUB"))
  }

  @Test
  fun `ofOrDefault should throw for invalid non-null labels`() {
    assertThrows(IllegalArgumentException::class.java) { OgiriTokenType.ofOrDefault("invalid") }
    assertThrows(IllegalArgumentException::class.java) { OgiriTokenType.ofOrDefault("") }
  }

  @Test
  fun `of should return correct type for valid labels`() {
    assertEquals(OgiriTokenType.APP, OgiriTokenType.of("app"))
    assertEquals(OgiriTokenType.SUB, OgiriTokenType.of("sub"))
  }

  @Test
  fun `of should throw for invalid or null labels`() {
    assertThrows(IllegalArgumentException::class.java) { OgiriTokenType.of("invalid") }
  }
}
