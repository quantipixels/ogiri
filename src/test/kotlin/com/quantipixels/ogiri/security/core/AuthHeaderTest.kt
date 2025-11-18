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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.Base64

class AuthHeaderTest {
  @Test
  fun `appendAuthHeaders emits authorization and sub token payload`() {
    val authHeader =
      AuthHeader(
        accessToken = "token-123",
        client = "client-a",
        uid = "user@example.com",
        expiry = "2030-01-01T00:00:00Z",
        kind = "APP",
        subTokens =
          mapOf(
            "device" to
              SubTokenHeader(
                client = "client-a.device",
                token = "sub123",
                expiry = "2030-01-01T00:00:00Z",
              ),
          ),
      )
    val response = MockHttpServletResponse()

    response.appendAuthHeaders(authHeader)

    assertEquals("token-123", response.getHeader(ACCESS_TOKEN))
    assertNotNull(response.getHeader("Authorization"))
    assertNotNull(response.getHeader("sub-tokens"))
    assertNull(response.getHeader("xmpp-password"))
  }

  @Test
  fun `extractAuthHeader reads primary headers`() {
    val response = MockHttpServletResponse()
    val authHeader =
      AuthHeader(
        accessToken = "token-abc",
        client = "client-b",
        uid = "user",
        expiry = "2030-01-01T00:00:00Z",
        kind = "APP",
      )
    response.appendAuthHeaders(authHeader)

    val encoded = response.getHeader("Authorization")!!.removePrefix("Bearer ").trim()
    val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    val request = MockHttpServletRequest()
    request.addHeader(ACCESS_TOKEN, "token-abc")
    request.addHeader(CLIENT, "client-b")
    request.addHeader(UID, "user")
    request.addHeader(EXPIRY, "2030-01-01T00:00:00Z")
    request.addHeader(ACCESS_TOKEN_KIND, "APP")

    val extracted = request.extractAuthHeader()

    assertEquals("token-abc", extracted.accessToken)
    assertEquals("client-b", extracted.client)
    assertEquals("user", extracted.uid)
    assertEquals("2030-01-01T00:00:00Z", extracted.expiry)
    assertEquals("APP", extracted.kind)
    assertNotNull(decoded)
  }
}
