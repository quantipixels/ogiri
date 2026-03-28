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
package com.quantipixels.ogiri.security.routes

import org.springframework.http.HttpMethod
import org.springframework.util.AntPathMatcher

/**
 * Aggregates registered [OgiriRouteRegistry] beans for quick lookup in filters and rate limiting.
 */
class OgiriRouteCatalog(
    registries: List<OgiriRouteRegistry>,
) {
  private val matcher = AntPathMatcher()
  private val configuredRoutes: List<OgiriRoute> = registries.flatMap { it.routes() }
  private val publicRoutes: List<OgiriRoute> = configuredRoutes.filterNot { it.useAuth }

  /** Routes configured by all registered [OgiriRouteRegistry] implementations. */
  fun configured(): List<OgiriRoute> = configuredRoutes

  /** Routes that do not require authentication (`useAuth = false`). */
  fun public(): List<OgiriRoute> = publicRoutes

  /**
   * Determines whether the given request URI and HTTP method match any configured public route.
   *
   * @param uri The request URI to test against registered routes.
   * @param method The HTTP method to match; when `null` the function will return `false`.
   * @return `true` if a configured public route matches the URI and method, `false` otherwise
   *   (including when `method` is `null`).
   */
  fun isPublicRoute(
      uri: String,
      method: HttpMethod?,
  ): Boolean {
    if (method == null) return false
    return publicRoutes.any { route ->
      route.method == method && matcher.match(route.pathWithWildcardVariables(), uri)
    }
  }
}
