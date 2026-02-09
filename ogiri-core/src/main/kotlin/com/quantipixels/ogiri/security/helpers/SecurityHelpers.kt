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

import jakarta.servlet.http.HttpServletRequest
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

object SecurityHelpers {
  private val logger = LoggerFactory.getLogger(SecurityHelpers::class.java)
  private val IP_PATTERN = Regex("^[\\d.:a-fA-F%]+$")

  /**
   * Validates whether the given string is a valid IP address (IPv4 or IPv6).
   *
   * Uses [InetAddress] for robust validation instead of regex patterns, which correctly handles:
   * - Standard IPv4 addresses (e.g., "192.168.1.1")
   * - Full IPv6 addresses (e.g., "2001:db8::1")
   * - Compressed IPv6 (e.g., "::", "::1")
   * - IPv4-mapped IPv6 (e.g., "::ffff:192.168.1.1")
   *
   * Note: IPv6 zone IDs (e.g., "fe80::1%eth0") are stripped before validation as they are
   * interface-specific suffixes that don't affect the validity of the IP address itself.
   *
   * @param ip The string to validate.
   * @return `true` if the string is a valid IPv4 or IPv6 address, `false` otherwise.
   */
  fun isValidIp(ip: String): Boolean {
    if (ip == "localhost") return true
    if (ip.isBlank()) return false

    // Strip IPv6 zone ID (e.g., "fe80::1%eth0" -> "fe80::1")
    val ipWithoutZone = ip.substringBefore('%')

    // Pre-check to prevent DNS resolution on non-IP inputs
    if (!IP_PATTERN.matches(ipWithoutZone)) return false

    return try {
      // InetAddress.getByName validates and parses the IP address
      val addr = InetAddress.getByName(ipWithoutZone)
      when (addr) {
        is Inet4Address -> {
          // InetAddress.getByName parses various formats as IPv4:
          // - Standard: "192.168.1.1" (valid)
          // - IPv4-mapped IPv6: "::ffff:192.168.1.1" (valid - special case)
          // - Single number: "12345" (invalid for our purposes)
          // - Partial: "192.168.1" (invalid for our purposes)
          //
          // Accept if it's either a proper 4-octet IPv4 or an IPv4-mapped IPv6 format
          val isStandardIpv4 =
              ipWithoutZone.count { it == '.' } == 3 &&
                  ipWithoutZone.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
          val isIpv4MappedIpv6 = ipWithoutZone.startsWith("::ffff:", ignoreCase = true)
          isStandardIpv4 || isIpv4MappedIpv6
        }
        is Inet6Address -> true
        else -> false
      }
    } catch (e: Exception) {
      when (e) {
        is UnknownHostException,
        is SecurityException,
        is IllegalArgumentException -> {
          logger.trace("IP validation failed (length={}): {}", ip.length, e.message)
          false
        }
        else -> throw e
      }
    }
  }

  /**
   * Extracts the client's IP address from the given servlet request.
   *
   * Returns request.remoteAddr which Spring Boot populates from the actual client IP when
   * server.forward-headers-strategy=NATIVE is configured. Manual header parsing is error-prone and
   * CVE-prone.
   *
   * @param request The incoming HTTP servlet request.
   * @return The client's IP address from request.remoteAddr.
   */
  fun getClientIP(request: HttpServletRequest): String? = request.remoteAddr

  val clientIP: String?
    get() {
      val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
      return attributes?.let { getClientIP(it.request) }
    }

  fun isPreflight(method: String?): Boolean = "OPTIONS".equals(method, ignoreCase = true)

  fun isAuthenticated(): Boolean = SecurityContextHolder.getContext().authentication != null
}
