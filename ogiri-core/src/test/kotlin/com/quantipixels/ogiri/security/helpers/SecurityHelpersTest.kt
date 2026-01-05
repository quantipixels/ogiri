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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class SecurityHelpersTest {

  @Nested
  inner class IsValidIpTests {

    @Test
    fun `valid IPv4 addresses return true`() {
      assertTrue(SecurityHelpers.isValidIp("192.168.1.1"))
      assertTrue(SecurityHelpers.isValidIp("10.0.0.1"))
      assertTrue(SecurityHelpers.isValidIp("127.0.0.1"))
      assertTrue(SecurityHelpers.isValidIp("8.8.8.8"))
      assertTrue(SecurityHelpers.isValidIp("0.0.0.0"))
      assertTrue(SecurityHelpers.isValidIp("255.255.255.255"))
    }

    @Test
    fun `invalid IPv4 addresses return false`() {
      // Note: The regex doesn't validate value ranges, so 256.1.1.1 matches
      // These are clearly invalid formats that don't match the pattern
      assertFalse(SecurityHelpers.isValidIp("192.168.1"))
      assertFalse(SecurityHelpers.isValidIp("192.168.1.1.1"))
      assertFalse(SecurityHelpers.isValidIp("not-an-ip"))
      assertFalse(SecurityHelpers.isValidIp("192.168.1."))
      assertFalse(SecurityHelpers.isValidIp(".192.168.1.1"))
      assertFalse(SecurityHelpers.isValidIp("abc.def.ghi.jkl"))
    }

    @Test
    fun `valid IPv6 addresses return true`() {
      // ::1 is explicitly handled as a special case
      assertTrue(SecurityHelpers.isValidIp("::1"))
      // Standard IPv6 format with multiple segments
      assertTrue(SecurityHelpers.isValidIp("2001:0db8:0000:0000:0000:0000:0000:0001"))
      assertTrue(SecurityHelpers.isValidIp("fe80:0000:0000:0000:0000:0000:0000:0001"))
    }

    @Test
    fun `invalid IPv6 addresses return false`() {
      // Contains invalid hex character 'g'
      assertFalse(SecurityHelpers.isValidIp("gggg:0000:0000:0000:0000:0000:0000:0001"))
      // Invalid format
      assertFalse(SecurityHelpers.isValidIp("not:an:ipv6"))
    }

    @Test
    fun `localhost is valid`() {
      assertTrue(SecurityHelpers.isValidIp("localhost"))
    }

    @Test
    fun `empty string is invalid`() {
      assertFalse(SecurityHelpers.isValidIp(""))
    }
  }

  @Nested
  inner class GetClientIPTests {

    @Test
    fun `extracts IP from X-Forwarded-For header`() {
      val request = MockHttpServletRequest().apply { addHeader("X-Forwarded-For", "192.168.1.100") }
      assertEquals("192.168.1.100", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `extracts first IP from comma-separated X-Forwarded-For`() {
      val request =
          MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "192.168.1.100, 10.0.0.1, 172.16.0.1")
          }
      assertEquals("192.168.1.100", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `trims whitespace from X-Forwarded-For IP`() {
      val request =
          MockHttpServletRequest().apply { addHeader("X-Forwarded-For", "  192.168.1.100  ") }
      assertEquals("192.168.1.100", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `skips X-Forwarded-For with unknown value`() {
      val request =
          MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "unknown")
            addHeader("X-Real-IP", "10.0.0.1")
          }
      assertEquals("10.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `skips X-Forwarded-For with UNKNOWN value case insensitive`() {
      val request =
          MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "UNKNOWN")
            addHeader("X-Real-IP", "10.0.0.1")
          }
      assertEquals("10.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `rejects invalid IP in X-Forwarded-For and falls back`() {
      val request =
          MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "not-an-ip")
            addHeader("X-Real-IP", "10.0.0.1")
          }
      assertEquals("10.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `falls back to X-Real-IP when X-Forwarded-For missing`() {
      val request = MockHttpServletRequest().apply { addHeader("X-Real-IP", "172.16.0.1") }
      assertEquals("172.16.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `skips X-Real-IP with unknown value`() {
      val request =
          MockHttpServletRequest("GET", "/test").apply {
            addHeader("X-Real-IP", "unknown")
            remoteAddr = "127.0.0.1"
          }
      assertEquals("127.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `rejects invalid IP in X-Real-IP and falls back to remoteAddr`() {
      val request =
          MockHttpServletRequest("GET", "/test").apply {
            addHeader("X-Real-IP", "invalid-ip")
            remoteAddr = "127.0.0.1"
          }
      assertEquals("127.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `falls back to remoteAddr when no headers present`() {
      val request = MockHttpServletRequest("GET", "/test").apply { remoteAddr = "192.168.0.1" }
      assertEquals("192.168.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `header priority is X-Forwarded-For then X-Real-IP then remoteAddr`() {
      val request =
          MockHttpServletRequest("GET", "/test").apply {
            addHeader("X-Forwarded-For", "1.1.1.1")
            addHeader("X-Real-IP", "2.2.2.2")
            remoteAddr = "3.3.3.3"
          }
      assertEquals("1.1.1.1", SecurityHelpers.getClientIP(request))
    }
  }

  @Nested
  inner class IsWhitelistedTests {

    @Test
    fun `null URI returns false`() {
      assertFalse(SecurityHelpers.isWhitelisted(null))
    }

    @Test
    fun `empty string returns false`() {
      assertFalse(SecurityHelpers.isWhitelisted(""))
    }

    @Test
    fun `swagger-ui paths are whitelisted`() {
      assertTrue(SecurityHelpers.isWhitelisted("/swagger-ui/index.html"))
      assertTrue(SecurityHelpers.isWhitelisted("/swagger-ui/swagger-ui.css"))
      assertTrue(SecurityHelpers.isWhitelisted("/swagger-ui.html"))
    }

    @Test
    fun `webjars paths are whitelisted`() {
      assertTrue(SecurityHelpers.isWhitelisted("/webjars/jquery/3.0/jquery.min.js"))
      assertTrue(SecurityHelpers.isWhitelisted("/webjars/bootstrap/5.0/css/bootstrap.css"))
    }

    @Test
    fun `openapi paths are whitelisted`() {
      assertTrue(SecurityHelpers.isWhitelisted("/openapi/v3/api-docs"))
      assertTrue(SecurityHelpers.isWhitelisted("/openapi/swagger-config"))
    }

    @Test
    fun `actuator health and info are whitelisted`() {
      assertTrue(SecurityHelpers.isWhitelisted("/actuator/health"))
      assertTrue(SecurityHelpers.isWhitelisted("/actuator/info"))
    }

    @Test
    fun `other actuator endpoints are NOT whitelisted`() {
      assertFalse(SecurityHelpers.isWhitelisted("/actuator/metrics"))
      assertFalse(SecurityHelpers.isWhitelisted("/actuator/env"))
      assertFalse(SecurityHelpers.isWhitelisted("/actuator/beans"))
    }

    @Test
    fun `favicon is whitelisted`() {
      assertTrue(SecurityHelpers.isWhitelisted("/favicon.ico"))
    }

    @Test
    fun `path traversal attempts are normalized and rejected`() {
      // /swagger-ui/../protected normalizes to /protected which is NOT whitelisted
      assertFalse(SecurityHelpers.isWhitelisted("/swagger-ui/../protected"))
      assertFalse(SecurityHelpers.isWhitelisted("/swagger-ui/../../etc/passwd"))
      assertFalse(SecurityHelpers.isWhitelisted("/actuator/health/../env"))
    }

    @Test
    fun `path traversal within whitelisted area still matches`() {
      // /swagger-ui/foo/../index.html normalizes to /swagger-ui/index.html which IS whitelisted
      assertTrue(SecurityHelpers.isWhitelisted("/swagger-ui/foo/../index.html"))
    }

    @Test
    fun `non-whitelisted paths return false`() {
      assertFalse(SecurityHelpers.isWhitelisted("/api/users"))
      assertFalse(SecurityHelpers.isWhitelisted("/admin"))
      assertFalse(SecurityHelpers.isWhitelisted("/protected/resource"))
    }
  }

  @Nested
  inner class IsPreflightTests {

    @Test
    fun `OPTIONS method is preflight`() {
      assertTrue(SecurityHelpers.isPreflight("OPTIONS"))
      assertTrue(SecurityHelpers.isPreflight("options"))
      assertTrue(SecurityHelpers.isPreflight("Options"))
    }

    @Test
    fun `other methods are not preflight`() {
      assertFalse(SecurityHelpers.isPreflight("GET"))
      assertFalse(SecurityHelpers.isPreflight("POST"))
      assertFalse(SecurityHelpers.isPreflight("PUT"))
      assertFalse(SecurityHelpers.isPreflight("DELETE"))
    }

    @Test
    fun `null method returns false`() {
      assertFalse(SecurityHelpers.isPreflight(null))
    }
  }
}
