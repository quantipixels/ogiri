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
