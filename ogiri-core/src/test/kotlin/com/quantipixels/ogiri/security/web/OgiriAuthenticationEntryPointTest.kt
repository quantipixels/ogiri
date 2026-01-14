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
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.ACCESS_TOKEN
import com.quantipixels.ogiri.security.core.CLIENT
import com.quantipixels.ogiri.security.core.EXPIRY
import com.quantipixels.ogiri.security.core.UID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException

class OgiriAuthenticationEntryPointTest {
  private val messageSource = StaticMessageSource()
  private val mapper = jacksonObjectMapper()

  private fun createProperties(cookiesEnabled: Boolean = true): OgiriConfigurationProperties {
    val properties = OgiriConfigurationProperties()
    properties.cookies.enabled = cookiesEnabled
    properties.cookies.path = "/"
    return properties
  }

  @Test
  fun `sends 401 response with JSON error payload`() {
    val properties = createProperties()
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException = BadCredentialsException("Invalid credentials")

    entryPoint.commence(request, response, exception)

    assertEquals(401, response.status)
    assertEquals(MediaType.APPLICATION_JSON_VALUE, response.contentType)
    val payload = mapper.readValue(response.contentAsString, Map::class.java)
    assertEquals(401, payload["status"])
    assertNotNull(payload["message"])
  }

  @Test
  fun `clears authentication cookies when cookies enabled on 401`() {
    val properties = createProperties(cookiesEnabled = true)
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException = BadCredentialsException("Invalid credentials")

    entryPoint.commence(request, response, exception)

    val cookies = response.cookies
    assertEquals(4, cookies.size, "Should clear all 4 auth cookies")

    val cookieNames = cookies.map { it.name }.toSet()
    assertTrue(cookieNames.contains(ACCESS_TOKEN), "Should clear access-token cookie")
    assertTrue(cookieNames.contains(CLIENT), "Should clear client cookie")
    assertTrue(cookieNames.contains(UID), "Should clear uid cookie")
    assertTrue(cookieNames.contains(EXPIRY), "Should clear expiry cookie")

    cookies.forEach { cookie ->
      assertEquals(0, cookie.maxAge, "Cookie ${cookie.name} should have maxAge=0")
      assertEquals("", cookie.value, "Cookie ${cookie.name} should have empty value")
      assertEquals("/", cookie.path, "Cookie ${cookie.name} should have correct path")
    }
  }

  @Test
  fun `does not clear cookies when cookies disabled on 401`() {
    val properties = createProperties(cookiesEnabled = false)
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException = BadCredentialsException("Invalid credentials")

    entryPoint.commence(request, response, exception)

    assertEquals(401, response.status)
    assertEquals(0, response.cookies.size, "Should not set any cookies when cookies disabled")
  }

  @Test
  fun `uses default message for BadCredentialsException when no custom message`() {
    val properties = createProperties()
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException = BadCredentialsException("Invalid credentials")

    entryPoint.commence(request, response, exception)

    val payload = mapper.readValue(response.contentAsString, Map::class.java)
    assertEquals("Invalid credentials", payload["message"])
  }

  @Test
  fun `uses default message for generic AuthenticationException when no custom message`() {
    val properties = createProperties()
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException =
        object : AuthenticationException("Generic auth error") {}

    entryPoint.commence(request, response, exception)

    val payload = mapper.readValue(response.contentAsString, Map::class.java)
    assertEquals("Authentication required", payload["message"])
  }

  @Test
  fun `clears cookies with custom cookie path`() {
    val properties = createProperties(cookiesEnabled = true)
    properties.cookies.path = "/api"
    val entryPoint = OgiriAuthenticationEntryPoint(messageSource, properties)
    val request = MockHttpServletRequest()
    val response = MockHttpServletResponse()
    val exception: AuthenticationException = BadCredentialsException("Invalid credentials")

    entryPoint.commence(request, response, exception)

    response.cookies.forEach { cookie ->
      assertEquals("/api", cookie.path, "Cookie ${cookie.name} should use custom path")
    }
  }
}
