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
package com.quantipixels.ogiri.security.web

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.ACCESS_TOKEN
import com.quantipixels.ogiri.security.core.CLIENT
import com.quantipixels.ogiri.security.core.EXPIRY
import com.quantipixels.ogiri.security.core.JsonCodec
import com.quantipixels.ogiri.security.core.UID
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

class OgiriAuthenticationEntryPoint(
    private val messageSource: MessageSource,
    private val properties: OgiriConfigurationProperties,
) : AuthenticationEntryPoint {
  private val logger = LoggerFactory.getLogger(OgiriAuthenticationEntryPoint::class.java)
  private val mapper = JsonCodec.mapper

  companion object {
    private val DEFAULT_MESSAGES =
        mapOf(
            "error.auth.bad_credentials" to "Invalid credentials",
            "error.auth.required" to "Authentication required",
        )
  }

  /**
   * Clears authentication cookies from the response.
   *
   * Sets all authentication-related cookies (access-token, client, uid, expiry) to empty values
   * with maxAge=0 to instruct the browser to delete them. This prevents clients from being stuck in
   * a 401 loop with stale credentials when using HttpOnly cookies.
   *
   * The cookies are cleared using the same path configured in the cookie properties to ensure
   * proper deletion scope.
   *
   * @param response The HTTP response where cookies will be cleared.
   */
  private fun clearAuthCookies(response: HttpServletResponse) {
    val cookieConfig = properties.cookies
    listOf(ACCESS_TOKEN, CLIENT, UID, EXPIRY).forEach { name ->
      val cookie =
          Cookie(name, "").apply {
            maxAge = 0
            path = cookieConfig.path
          }
      response.addCookie(cookie)
    }
    logger.debug("Cleared authentication cookies on 401 response")
  }

  /**
   * Sends a 401 JSON response containing a localized authentication error message.
   *
   * The response uses a message key chosen based on the exception type:
   * - "error.auth.bad_credentials" when the exception is BadCredentialsException
   * - "error.auth.required" for other authentication failures
   *
   * If no message is found in the MessageSource, a default message is used.
   *
   * When cookies are enabled (ogiri.cookies.enabled=true), this method also clears authentication
   * cookies to prevent clients from being stuck in a 401 loop with stale credentials. This aligns
   * with OWASP session management best practices for handling authentication failures.
   *
   * @param request The incoming HTTP request that triggered authentication.
   * @param response The HTTP response that will be populated with status 401 and a JSON payload.
   * @param authException The authentication exception used to determine the localized error
   *   message.
   */
  override fun commence(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authException: AuthenticationException,
  ) {
    logger.debug("Unauthorized request: {}", authException.message)

    // Clear authentication cookies if cookies are enabled to prevent 401 loops
    if (properties.cookies.enabled) {
      clearAuthCookies(response)
    }

    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.status = HttpServletResponse.SC_UNAUTHORIZED

    val locale = LocaleContextHolder.getLocale()
    val code =
        when (authException) {
          is BadCredentialsException -> "error.auth.bad_credentials"
          else -> "error.auth.required"
        }
    val message =
        messageSource.getMessage(
            code, null, DEFAULT_MESSAGES[code] ?: "Authentication error", locale)
    val payload = mapOf("status" to HttpServletResponse.SC_UNAUTHORIZED, "message" to message)
    mapper.writeValue(response.outputStream, payload)
  }
}
