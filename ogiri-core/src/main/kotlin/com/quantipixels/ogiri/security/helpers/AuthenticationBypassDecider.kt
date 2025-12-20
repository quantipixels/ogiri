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
package com.quantipixels.ogiri.security.helpers

import com.quantipixels.ogiri.security.routes.OgiriRouteCatalog
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Centralizes logic to skip authentication when appropriate (already authenticated, whitelisted
 * paths, CORS preflight, or public routes).
 */
class AuthenticationBypassDecider(
    private val routeCatalog: OgiriRouteCatalog,
) {
  /**
   * Determines whether authentication may be skipped for the given HTTP request.
   *
   * @param request The incoming HTTP servlet request to evaluate.
   * @return `true` if the request is already authenticated, matches a whitelist entry, is a CORS preflight request, or targets a public route; `false` otherwise.
   */
  fun canSkip(request: HttpServletRequest): Boolean {
    val isAuthenticated = SecurityContextHolder.getContext().authentication != null
    val isWhitelisted = SecurityHelpers.isWhitelisted(request.requestURI)
    val isPreflight = SecurityHelpers.isPreflight(request.method)
    val method = runCatching { HttpMethod.valueOf(request.method) }.getOrNull()
    val isPublicRoute = method?.let { routeCatalog.isPublicRoute(request.requestURI, it) } ?: false
    return isAuthenticated || isWhitelisted || isPreflight || isPublicRoute
  }
}
