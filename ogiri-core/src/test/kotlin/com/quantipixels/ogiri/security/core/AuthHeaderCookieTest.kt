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
package com.quantipixels.ogiri.security.core

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse

class AuthHeaderCookieTest {

  private lateinit var response: MockHttpServletResponse
  private lateinit var cookieConfig: OgiriConfigurationProperties.CookieProperties

  @BeforeEach
  fun setUp() {
    response = MockHttpServletResponse()
    cookieConfig =
        OgiriConfigurationProperties.CookieProperties().apply {
          enabled = true
          secure = true
          httpOnly = true
          sameSite = "Strict"
          path = "/"
        }
  }

  @Nested
  inner class AppendAuthCookiesTests {

    @Test
    fun `sets expected cookies and security attributes`() {
      val authHeader =
          AuthHeader(
              accessToken = "test-token",
              client = "test-client",
              uid = "test-user",
              expiry = "1234567890",
          )
      cookieConfig.path = "/api"
      cookieConfig.sameSite = "Lax"

      response.appendAuthCookies(authHeader, cookieConfig)

      assertEquals(4, response.cookies.size)
      assertEquals("test-token", response.cookies.find { it.name == ACCESS_TOKEN }?.value)
      assertEquals("test-client", response.cookies.find { it.name == CLIENT }?.value)
      assertEquals("test-user", response.cookies.find { it.name == UID }?.value)
      assertEquals("1234567890", response.cookies.find { it.name == EXPIRY }?.value)
      response.cookies.forEach { cookie ->
        assertTrue(cookie.isHttpOnly)
        assertTrue(cookie.secure)
        assertEquals("/api", cookie.path)
        assertEquals("Lax", cookie.getAttribute("SameSite"))
      }
    }

    @Test
    fun `skips cookies for blank fields`() {
      val authHeader = AuthHeader(accessToken = "", client = "client", uid = null, expiry = "12345")

      response.appendAuthCookies(authHeader, cookieConfig)

      assertNull(response.cookies.find { it.name == ACCESS_TOKEN })
      assertNull(response.cookies.find { it.name == UID })
      assertEquals(2, response.cookies.size)
      assertEquals("client", response.cookies.find { it.name == CLIENT }?.value)
      assertEquals("12345", response.cookies.find { it.name == EXPIRY }?.value)
    }
  }

  @Nested
  inner class AppendAuthHeadersWithCookiesTests {

    @Test
    fun `sets both headers and cookies when enabled`() {
      val authHeader =
          AuthHeader(accessToken = "token", client = "client", uid = "user", expiry = "12345")
      cookieConfig.enabled = true

      response.appendAuthHeaders(authHeader, cookieConfig)

      assertEquals("token", response.getHeader(ACCESS_TOKEN))
      assertEquals("client", response.getHeader(CLIENT))
      assertEquals("user", response.getHeader(UID))
      assertEquals(4, response.cookies.size)
      assertEquals("token", response.cookies.find { it.name == ACCESS_TOKEN }?.value)
    }

    @Test
    fun `does not set cookies when cookieConfig disabled`() {
      val authHeader =
          AuthHeader(accessToken = "token", client = "client", uid = "user", expiry = "12345")
      cookieConfig.enabled = false

      response.appendAuthHeaders(authHeader, cookieConfig)

      assertEquals(0, response.cookies.size)
    }

    @Test
    fun `does not set cookies when cookieConfig null`() {
      val authHeader =
          AuthHeader(accessToken = "token", client = "client", uid = "user", expiry = "12345")

      response.appendAuthHeaders(authHeader, null)

      assertEquals(0, response.cookies.size)
    }

    @Test
    fun `null authHeaders leaves response unchanged`() {
      response.appendAuthHeaders(null, cookieConfig)

      assertEquals(0, response.cookies.size)
      assertNull(response.getHeader(ACCESS_TOKEN))
    }
  }
}
