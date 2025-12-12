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
 * Used to distinguish between primary application tokens (APP) and sub-tokens (SUB) for specialized
 * use cases (e.g., device tokens, chat tokens).
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
 *   val tokenType: String = "APP"  // or use @Enumerated(EnumType.STRING)
 * }
 * ```
 *
 * Example - JDBC Token with manual conversion:
 * ```kotlin
 * data class JdbcToken(
 *   override val tokenType: String,  // stored as "APP" or "SUB"
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
     * Parse a OgiriTokenType from a string label (case-insensitive).
     *
     * @param label The token type label ("app" or "sub")
     * @return The corresponding OgiriTokenType
     * @throws IllegalArgumentException if label doesn't match any type
     */
    fun of(label: String): OgiriTokenType =
        entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?: throw IllegalArgumentException("Invalid token type: $label")

    /**
     * Parse a OgiriTokenType from a string, with fallback to APP type if invalid.
     *
     * Useful for database conversions where null/missing values should default to APP.
     *
     * @param label The token type label ("app" or "sub")
     * @return The corresponding OgiriTokenType, or APP if label is null or invalid
     */
    fun ofOrDefault(label: String?): OgiriTokenType = label?.let { ofNullable(it) } ?: APP

    /**
     * Parse a OgiriTokenType from a nullable string label.
     *
     * @param label The token type label ("app" or "sub"), or null
     * @return The corresponding OgiriTokenType, or APP if label is null or invalid
     */
    private fun ofNullable(label: String?): OgiriTokenType =
        label?.let { value -> entries.firstOrNull { it.label.equals(value, ignoreCase = true) } }
            ?: APP
  }
}
