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

/**
 * Token type classifier enumeration.
 *
 * Used to distinguish between primary application tokens ("app") and sub-tokens ("sub") for
 * specialized use cases (e.g., device tokens, chat tokens).
 *
 * Implementations can persist this as:
 * - A string column (storing the label: "app" or "sub")
 * - An enum column (JPA @Enumerated)
 * - A numeric column with custom conversion logic
 *
 * Example - JPA Entity with string mapping:
 * ```kotlin
 * @Entity
 * class MyToken : OgiriBaseToken() {
 *   @Column(name = "token_type", nullable = false)
 *   val tokenType: String = "app"  // or use @Enumerated(EnumType.STRING)
 * }
 * ```
 *
 * Example - JDBC Token with manual conversion:
 * ```kotlin
 * data class JdbcToken(
 *   override val tokenType: String,  // stored as "app" or "sub"
 * ) : OgiriBaseToken()
 * ```
 */
enum class OgiriTokenType(val label: String) {
  /** Primary application token for user authentication and authorization. */
  APP("app"),

  /** Sub-token for specialized use cases (device, chat, API, etc.). */
  SUB("sub"),
  ;

  companion object {
    /**
             * Parse an OgiriTokenType from the given label using a case-insensitive match.
             *
             * @param label The token type label, expected to be "app" or "sub".
             * @return The `OgiriTokenType` that corresponds to the provided label.
             * @throws IllegalArgumentException if the label does not match any token type.
             */
    fun of(label: String): OgiriTokenType =
        entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid token type: $label")

    /**
     * Parse an OgiriTokenType from a label, defaulting to APP when the label is null.
     *
     * @param label The token type label ("app" or "sub"), or null to use the APP default.
     * @return The matching OgiriTokenType, or APP if label is null.
     * @throws IllegalArgumentException if label is non-null and does not match any known type.
     */
    fun ofOrDefault(label: String?): OgiriTokenType {
      // Use strict semantics: null defaults to APP, but invalid non-null labels are errors.
      if (label == null) return APP
      return entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
          ?: throw IllegalArgumentException("Invalid token type label: $label")
    }

    /**
             * Resolve an OgiriTokenType from an optional label, defaulting to APP when no match is found.
             *
             * @param label The token type label (case-insensitive, expected "app" or "sub"), or null.
             * @return The matching OgiriTokenType, or APP when `label` is null or does not match any known type.
             */
    private fun ofNullable(label: String?): OgiriTokenType =
        label?.let { value -> entries.firstOrNull { it.label.equals(value, ignoreCase = true) } }
            ?: APP
  }
}
