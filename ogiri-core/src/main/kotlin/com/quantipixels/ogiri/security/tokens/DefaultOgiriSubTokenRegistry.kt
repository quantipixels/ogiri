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
 * Simple registry implementation backed by a provided list of [OgiriSubTokenRegistration] beans.
 */
class DefaultOgiriSubTokenRegistry(
    private val registrations: List<OgiriSubTokenRegistration> = emptyList(),
) : OgiriSubTokenRegistry {
  /**
   * Provides the list of registered Ogiri sub-token registrations.
   *
   * @return The list of `OgiriSubTokenRegistration` instances held by this registry.
   */
  override fun registrations(): List<OgiriSubTokenRegistration> = registrations
}
