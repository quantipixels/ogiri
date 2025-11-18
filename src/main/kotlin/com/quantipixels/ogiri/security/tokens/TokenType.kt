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

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class TokenType(val label: String) {
  APP("app"),
  SUB("sub"),
  ;

  companion object {
    fun of(label: String) =
      entries.firstOrNull {
        it.label.equals(label, ignoreCase = true)
      } ?: throw IllegalArgumentException("Invalid token type: $label")
  }
}

@Converter(autoApply = true)
class TokenTypeConverter : AttributeConverter<TokenType, String> {
  override fun convertToDatabaseColumn(value: TokenType?): String? = value?.label

  override fun convertToEntityAttribute(dbData: String?): TokenType = dbData?.let { TokenType.of(it) } ?: TokenType.APP
}
