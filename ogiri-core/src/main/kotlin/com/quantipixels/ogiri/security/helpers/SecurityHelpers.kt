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
import java.net.URI
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

val pathMatcher = AntPathMatcher()

// IP validation patterns
private val IPV4_PATTERN = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
private val IPV6_PATTERN = Regex("^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$")

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

  /**
   * Validates whether the given string is a valid IP address (IPv4 or IPv6).
   *
   * @param ip The string to validate.
   * @return `true` if the string matches IPv4 or IPv6 format, `false` otherwise.
   */
  fun isValidIp(ip: String): Boolean =
      IPV4_PATTERN.matches(ip) || IPV6_PATTERN.matches(ip) || ip == "::1" || ip == "localhost"

  /**
   * Extracts the client's IP address from the given servlet request.
   *
   * Prefers the first value from the `X-Forwarded-For` header when present, valid, and not
   * "unknown", falls back to `X-Real-IP` when present, valid, and not "unknown", and otherwise
   * returns `request.remoteAddr`. IP addresses are validated before being returned to prevent IP
   * spoofing attacks.
   *
   * @param request The incoming HTTP servlet request.
   * @return The client's IP address, or `null` if no valid address can be determined.
   */
  fun getClientIP(request: HttpServletRequest): String? {
    val xForwardedFor = request.getHeader("X-Forwarded-For")
    if (!xForwardedFor.isNullOrBlank() && !xForwardedFor.equals("unknown", ignoreCase = true)) {
      val ip = xForwardedFor.split(',')[0].trim()
      if (isValidIp(ip)) return ip
    }
    val xRealIp = request.getHeader("X-Real-IP")
    if (!xRealIp.isNullOrBlank() && !xRealIp.equals("unknown", ignoreCase = true)) {
      if (isValidIp(xRealIp)) return xRealIp
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
    // Normalize path to prevent traversal bypass (e.g., /swagger-ui/../protected)
    val normalized =
        try {
          URI(uri).normalize().path ?: uri
        } catch (_: Exception) {
          uri
        }
    return WHITE_LIST_PREFIXES.any { pathMatcher.match(it, normalized) } ||
        normalized == "/favicon.ico"
  }

  fun isPreflight(method: String?): Boolean = "OPTIONS".equals(method, ignoreCase = true)

  fun isAuthenticated(): Boolean = SecurityContextHolder.getContext().authentication != null
}
