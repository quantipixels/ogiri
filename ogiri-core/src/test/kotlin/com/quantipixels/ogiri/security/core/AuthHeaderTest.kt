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

import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AuthHeaderTest {
  @Test
  fun `appendAuthHeaders emits authorization and sub token payload`() {
    val authHeader =
        AuthHeader(
            accessToken = "token-123",
            client = "client-a",
            uid = "user@example.com",
            expiry = "2030-01-01T00:00:00Z",
            kind = "app",
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
    assertNotNull(response.getHeader("device"))
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
            kind = "app",
        )
    response.appendAuthHeaders(authHeader)

    val encoded = response.getHeader("Authorization")!!.removePrefix("Bearer ").trim()
    val decoded = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    val request = MockHttpServletRequest()
    request.addHeader(ACCESS_TOKEN, "token-abc")
    request.addHeader(CLIENT, "client-b")
    request.addHeader(UID, "user")
    request.addHeader(EXPIRY, "2030-01-01T00:00:00Z")
    request.addHeader(ACCESS_TOKEN_KIND, "app")

    val extracted = request.extractAuthHeader()

    assertEquals("token-abc", extracted.accessToken)
    assertEquals("client-b", extracted.client)
    assertEquals("user", extracted.uid)
    assertEquals("2030-01-01T00:00:00Z", extracted.expiry)
    assertEquals("app", extracted.kind)
    assertNotNull(decoded)
  }

  @Nested
  inner class ParseBearerTokenTests {

    @Test
    fun `parseBearerToken parses valid token`() {
      val payload =
          mapOf(
              ACCESS_TOKEN to "token-123",
              CLIENT to "client-a",
              UID to "user@example.com",
              EXPIRY to "2030-01-01T00:00:00Z",
          )
      val json = JsonCodec.mapper.writeValueAsString(payload)
      val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

      val result = parseBearerToken("Bearer $encoded")

      assertNotNull(result)
      assertEquals("token-123", result!![ACCESS_TOKEN])
      assertEquals("client-a", result[CLIENT])
      assertEquals("user@example.com", result[UID])
    }

    @Test
    fun `parseBearerToken parses token without Bearer prefix`() {
      val payload = mapOf(ACCESS_TOKEN to "token-456")
      val json = JsonCodec.mapper.writeValueAsString(payload)
      val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

      val result = parseBearerToken(encoded)

      assertNotNull(result)
      assertEquals("token-456", result!![ACCESS_TOKEN])
    }

    @Test
    fun `parseBearerToken returns null for invalid base64`() {
      val result = parseBearerToken("Bearer not-valid-base64!!!")

      assertNull(result)
    }

    @Test
    fun `parseBearerToken returns null for invalid JSON`() {
      val encoded = Base64.getEncoder().encodeToString("not json".toByteArray(Charsets.UTF_8))

      val result = parseBearerToken("Bearer $encoded")

      assertNull(result)
    }

    @Test
    fun `parseBearerToken rejects token exceeding max size`() {
      // Create a token larger than DEFAULT_MAX_BEARER_TOKEN_SIZE
      val largePayload = "x".repeat(DEFAULT_MAX_BEARER_TOKEN_SIZE + 1)

      val result = parseBearerToken("Bearer $largePayload")

      assertNull(result)
    }

    @Test
    fun `parseBearerToken accepts token at max size boundary`() {
      // Create a valid token that's just under the limit
      val payload = mapOf(ACCESS_TOKEN to "a".repeat(100))
      val json = JsonCodec.mapper.writeValueAsString(payload)
      val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

      // Ensure this is under the limit
      assertTrue(encoded.length <= DEFAULT_MAX_BEARER_TOKEN_SIZE)

      val result = parseBearerToken("Bearer $encoded")

      assertNotNull(result)
    }

    @Test
    fun `parseBearerToken rejects token where decoded content exceeds max size`() {
      // Create a payload that when decoded exceeds max size but encoded might be close
      // Base64 expands by ~33%, so we need content that's large enough after decoding
      val largeValue = "x".repeat(DEFAULT_MAX_BEARER_TOKEN_SIZE + 1000)
      val payload = mapOf("data" to largeValue)
      val json = JsonCodec.mapper.writeValueAsString(payload)
      val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

      // If the encoded version is already too large, it will be rejected at that stage
      // Otherwise, the decoded JSON size check will catch it
      val result = parseBearerToken("Bearer $encoded")

      assertNull(result)
    }
  }
}
