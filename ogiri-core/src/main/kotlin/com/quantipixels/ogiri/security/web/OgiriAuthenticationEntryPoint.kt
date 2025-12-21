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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
) : AuthenticationEntryPoint {
  private val logger = LoggerFactory.getLogger(OgiriAuthenticationEntryPoint::class.java)
  private val mapper = jacksonObjectMapper()

  /**
   * Sends a 401 JSON response containing a localized authentication error message.
   *
   * The response uses a message key chosen based on the exception type:
   * - "error.auth.bad_credentials" when the exception is BadCredentialsException
   * - "error.auth.required" for other authentication failures
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
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    response.status = HttpServletResponse.SC_UNAUTHORIZED

    val locale = LocaleContextHolder.getLocale()
    val code =
        when (authException) {
          is BadCredentialsException -> "error.auth.bad_credentials"
          else -> "error.auth.required"
        }
    val message = messageSource.getMessage(code, null, locale)
    val payload = mapOf("status" to HttpServletResponse.SC_UNAUTHORIZED, "message" to message)
    mapper.writeValue(response.outputStream, payload)
  }
}
