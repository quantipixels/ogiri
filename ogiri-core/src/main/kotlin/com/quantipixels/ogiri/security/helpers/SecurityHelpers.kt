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

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

val pathMatcher = AntPathMatcher()

object SecurityHelpers {
  val WHITE_LIST_PREFIXES: List<String> =
      listOf(
          "/swagger-ui/**",
          "/swagger-ui.html",
          "/webjars/**",
          "/openapi/**",
          "/actuator/health",
          "/actuator/info",
      )

  fun getClientIP(request: HttpServletRequest): String? {
    val xForwardedFor = request.getHeader("X-Forwarded-For")
    if (!xForwardedFor.isNullOrBlank() && !xForwardedFor.equals("unknown", ignoreCase = true)) {
      return xForwardedFor.split(',')[0].trim()
    }
    val xRealIp = request.getHeader("X-Real-IP")
    if (!xRealIp.isNullOrBlank() && !xRealIp.equals("unknown", ignoreCase = true)) {
      return xRealIp
    }
    return request.remoteAddr
  }

  val clientIP: String?
    get() {
      val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
      return attributes?.let { getClientIP(it.request) }
    }

  fun isWhitelisted(uri: String?): Boolean {
    if (uri == null) return false
    return WHITE_LIST_PREFIXES.any { uri.startsWith(it) } || uri == "/favicon.ico"
  }

  fun isPreflight(method: String?): Boolean = "OPTIONS".equals(method, ignoreCase = true)

  fun isAuthenticated(): Boolean = SecurityContextHolder.getContext().authentication != null
}
