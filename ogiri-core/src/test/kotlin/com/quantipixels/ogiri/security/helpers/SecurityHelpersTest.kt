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
      // Loopback
      assertTrue(SecurityHelpers.isValidIp("::1"))
      // Unspecified address
      assertTrue(SecurityHelpers.isValidIp("::"))
      // Standard IPv6 format with multiple segments
      assertTrue(SecurityHelpers.isValidIp("2001:0db8:0000:0000:0000:0000:0000:0001"))
      assertTrue(SecurityHelpers.isValidIp("fe80:0000:0000:0000:0000:0000:0000:0001"))
      // Compressed IPv6 (various positions)
      assertTrue(SecurityHelpers.isValidIp("2001:db8::1"))
      assertTrue(SecurityHelpers.isValidIp("2001:db8:85a3::8a2e:370:7334"))
    }

    @Test
    fun `IPv4-mapped IPv6 addresses are valid`() {
      // IPv4-mapped IPv6 is valid (Java parses it as Inet4Address internally but it's still valid)
      // Note: InetAddress.getByName("::ffff:192.168.1.1") returns Inet4Address
      assertTrue(SecurityHelpers.isValidIp("::ffff:192.168.1.1"))
    }

    @Test
    fun `IPv6 with zone IDs are valid`() {
      // Link-local with zone ID (commonly used in network interfaces)
      assertTrue(SecurityHelpers.isValidIp("fe80::1%eth0"))
      assertTrue(SecurityHelpers.isValidIp("fe80::1%en0"))
      assertTrue(SecurityHelpers.isValidIp("fe80::1%1"))
    }

    @Test
    fun `invalid IPv6 addresses return false`() {
      // Contains invalid hex character 'g'
      assertFalse(SecurityHelpers.isValidIp("gggg:0000:0000:0000:0000:0000:0000:0001"))
      // Invalid format - too few colons
      assertFalse(SecurityHelpers.isValidIp("not:an:ipv6"))
      // Invalid - too many segments
      assertFalse(SecurityHelpers.isValidIp("1:2:3:4:5:6:7:8:9"))
      // Invalid - empty segments without proper compression
      assertFalse(SecurityHelpers.isValidIp("1::2::3"))
      // Invalid - just colons (was previously accepted by regex)
      assertFalse(SecurityHelpers.isValidIp("::::::::"))
    }

    @Test
    fun `IPv4 addresses with out-of-range octets are invalid`() {
      // InetAddress correctly rejects these
      assertFalse(SecurityHelpers.isValidIp("256.1.1.1"))
      assertFalse(SecurityHelpers.isValidIp("192.168.1.256"))
      assertFalse(SecurityHelpers.isValidIp("999.999.999.999"))
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
    fun `returns remoteAddr directly`() {
      val request = MockHttpServletRequest("GET", "/test").apply { remoteAddr = "192.168.1.100" }
      assertEquals("192.168.1.100", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `ignores X-Forwarded-For header`() {
      val request =
          MockHttpServletRequest("GET", "/test").apply {
            addHeader("X-Forwarded-For", "1.1.1.1")
            remoteAddr = "127.0.0.1"
          }
      assertEquals("127.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `ignores X-Real-IP header`() {
      val request =
          MockHttpServletRequest("GET", "/test").apply {
            addHeader("X-Real-IP", "2.2.2.2")
            remoteAddr = "127.0.0.1"
          }
      assertEquals("127.0.0.1", SecurityHelpers.getClientIP(request))
    }

    @Test
    fun `returns default MockHttpServletRequest remoteAddr when not set`() {
      val request = MockHttpServletRequest("GET", "/test")
      // MockHttpServletRequest defaults to "127.0.0.1"
      assertEquals("127.0.0.1", SecurityHelpers.getClientIP(request))
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
