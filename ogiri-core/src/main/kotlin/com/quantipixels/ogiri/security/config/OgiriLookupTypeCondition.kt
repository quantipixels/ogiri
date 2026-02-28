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

import org.springframework.boot.autoconfigure.condition.ConditionMessage
import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * Shared [SpringBootCondition] that matches when `ogiri.lookup.type` equals [type].
 *
 * Extend with a no-arg subclass to use with `@Conditional`:
 * ```kotlin
 * internal class OnCaffeineType : OgiriLookupTypeCondition("caffeine")
 * ```
 */
open class OgiriLookupTypeCondition(private val type: String) : SpringBootCondition() {
  override fun getMatchOutcome(
      context: ConditionContext,
      metadata: AnnotatedTypeMetadata,
  ): ConditionOutcome {
    val normalized = context.environment.getProperty("ogiri.lookup.type")?.trim()?.lowercase() ?: ""
    return if (normalized == type) {
      ConditionOutcome(
          true,
          ConditionMessage.forCondition("OgiriLookupType[$type]")
              .found("property")
              .items("ogiri.lookup.type=$type"),
      )
    } else {
      ConditionOutcome(
          false,
          ConditionMessage.forCondition("OgiriLookupType[$type]")
              .didNotFind("property with value '$type'")
              .items("ogiri.lookup.type"),
      )
    }
  }
}
