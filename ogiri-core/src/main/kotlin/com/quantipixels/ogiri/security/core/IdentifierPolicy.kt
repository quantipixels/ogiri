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

/**
 * Minimum character length for generated identifiers.
 *
 * Enforced by [DefaultIdentifierPolicy] on construction. Custom [IdentifierPolicy] implementations
 * should respect this floor to maintain the library's security baseline.
 */
const val MIN_TOKEN_LENGTH = 32

/**
 * Strategy for generating and validating opaque string identifiers used as token values and client
 * IDs.
 *
 * Implementations must produce cryptographically random strings of sufficient entropy.
 * [DefaultIdentifierPolicy] uses [java.security.SecureRandom] over the URL-safe alphabet
 * `A-Za-z0-9._-` with a minimum length of [MIN_TOKEN_LENGTH].
 */
interface IdentifierPolicy {
  fun generate(): String

  /**
   * Returns `true` if [value] is a non-blank string that conforms to this policy's identifier
   * format. `null` and blank strings always return `false`.
   */
  fun isValid(value: String?): Boolean
}

class DefaultIdentifierPolicy(
    private val length: Int = MIN_TOKEN_LENGTH,
) : IdentifierPolicy {
  init {
    require(length >= MIN_TOKEN_LENGTH) {
      "Token length must be at least $MIN_TOKEN_LENGTH characters (configured: $length)"
    }
  }

  private val random = SecureRandom()
  private val alphabet =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-".toCharArray()
  private val validator = Regex("^[A-Za-z0-9._-]{$MIN_TOKEN_LENGTH,${length.coerceAtLeast(64)}}$")

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
