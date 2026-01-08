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
package com.quantipixels.ogiri.security.core

import java.security.SecureRandom

interface IdentifierPolicy {
  fun generate(): String

  fun isValid(value: String?): Boolean
}

class DefaultIdentifierPolicy(
    private val length: Int = 32,
) : IdentifierPolicy {
  private val random = SecureRandom()
  private val alphabet =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-".toCharArray()
  private val validator = Regex("^[A-Za-z0-9._-]{1,64}$")

  /**
   * Generates an identifier of the configured length composed of characters from the allowed
   * alphabet.
   *
   * @return A string identifier whose length equals the policy's `length` and that contains only
   *   characters from `A–Z`, `a–z`, `0–9`, `.`, `_`, and `-`.
   */
  override fun generate(): String {
    val builder = StringBuilder(length)
    repeat(length) { builder.append(alphabet[random.nextInt(alphabet.size)]) }
    return builder.toString()
  }

  override fun isValid(value: String?): Boolean = !value.isNullOrBlank() && validator.matches(value)
}
