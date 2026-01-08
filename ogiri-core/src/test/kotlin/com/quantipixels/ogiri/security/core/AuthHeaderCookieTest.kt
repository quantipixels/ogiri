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
    fun `sets all four auth cookies`() {
      val authHeader =
          AuthHeader(
              accessToken = "test-token",
              client = "test-client",
              uid = "test-user",
              expiry = "1234567890",
          )

      response.appendAuthCookies(authHeader, cookieConfig)

      val cookies = response.cookies
      assertEquals(4, cookies.size)

      val cookieNames = cookies.map { it.name }.toSet()
      assertTrue(cookieNames.contains(ACCESS_TOKEN))
      assertTrue(cookieNames.contains(CLIENT))
      assertTrue(cookieNames.contains(UID))
      assertTrue(cookieNames.contains(EXPIRY))
    }

    @Test
    fun `sets HttpOnly flag correctly`() {
      val authHeader = AuthHeader(accessToken = "token", client = "client", uid = "user")
      cookieConfig.httpOnly = true

      response.appendAuthCookies(authHeader, cookieConfig)

      response.cookies.forEach { cookie -> assertTrue(cookie.isHttpOnly) }
    }

    @Test
    fun `sets Secure flag correctly`() {
      val authHeader = AuthHeader(accessToken = "token", client = "client", uid = "user")
      cookieConfig.secure = true

      response.appendAuthCookies(authHeader, cookieConfig)

      response.cookies.forEach { cookie -> assertTrue(cookie.secure) }
    }

    @Test
    fun `sets SameSite Strict attribute`() {
      val authHeader = AuthHeader(accessToken = "token", client = "client", uid = "user")
      cookieConfig.sameSite = "Strict"

      response.appendAuthCookies(authHeader, cookieConfig)

      response.cookies.forEach { cookie -> assertEquals("Strict", cookie.getAttribute("SameSite")) }
    }

    @Test
    fun `sets SameSite Lax attribute`() {
      val authHeader = AuthHeader(accessToken = "token", client = "client", uid = "user")
      cookieConfig.sameSite = "Lax"

      response.appendAuthCookies(authHeader, cookieConfig)

      response.cookies.forEach { cookie -> assertEquals("Lax", cookie.getAttribute("SameSite")) }
    }

    @Test
    fun `sets cookie path correctly`() {
      val authHeader = AuthHeader(accessToken = "token", client = "client", uid = "user")
      cookieConfig.path = "/api"

      response.appendAuthCookies(authHeader, cookieConfig)

      response.cookies.forEach { cookie -> assertEquals("/api", cookie.path) }
    }

    @Test
    fun `skips cookie for blank access token`() {
      val authHeader = AuthHeader(accessToken = "", client = "client", uid = "user")

      response.appendAuthCookies(authHeader, cookieConfig)

      val accessTokenCookie = response.cookies.find { it.name == ACCESS_TOKEN }
      assertNull(accessTokenCookie)
    }

    @Test
    fun `skips cookie for null access token`() {
      val authHeader = AuthHeader(accessToken = null, client = "client", uid = "user")

      response.appendAuthCookies(authHeader, cookieConfig)

      val accessTokenCookie = response.cookies.find { it.name == ACCESS_TOKEN }
      assertNull(accessTokenCookie)
    }

    @Test
    fun `cookie values match auth header values`() {
      val authHeader =
          AuthHeader(
              accessToken = "my-token-value",
              client = "my-client-id",
              uid = "my-user-id",
              expiry = "9999999999",
          )

      response.appendAuthCookies(authHeader, cookieConfig)

      assertEquals("my-token-value", response.cookies.find { it.name == ACCESS_TOKEN }?.value)
      assertEquals("my-client-id", response.cookies.find { it.name == CLIENT }?.value)
      assertEquals("my-user-id", response.cookies.find { it.name == UID }?.value)
      assertEquals("9999999999", response.cookies.find { it.name == EXPIRY }?.value)
    }
  }

  @Nested
  inner class AppendAuthHeadersWithCookiesTests {

    @Test
    fun `sets cookies when cookieConfig enabled`() {
      val authHeader =
          AuthHeader(accessToken = "token", client = "client", uid = "user", expiry = "12345")
      cookieConfig.enabled = true

      response.appendAuthHeaders(authHeader, cookieConfig)

      assertTrue(response.cookies.isNotEmpty())
      assertEquals(4, response.cookies.size)
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
    fun `sets both headers and cookies when enabled`() {
      val authHeader =
          AuthHeader(accessToken = "token", client = "client", uid = "user", expiry = "12345")
      cookieConfig.enabled = true

      response.appendAuthHeaders(authHeader, cookieConfig)

      // Check headers are set
      assertEquals("token", response.getHeader(ACCESS_TOKEN))
      assertEquals("client", response.getHeader(CLIENT))
      assertEquals("user", response.getHeader(UID))

      // Check cookies are also set
      assertEquals(4, response.cookies.size)
      assertEquals("token", response.cookies.find { it.name == ACCESS_TOKEN }?.value)
    }

    @Test
    fun `null authHeaders does not throw and sets nothing`() {
      response.appendAuthHeaders(null, cookieConfig)

      assertEquals(0, response.cookies.size)
      assertNull(response.getHeader(ACCESS_TOKEN))
    }
  }
}
