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
package com.quantipixels.ogiri.samples.kotlin.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Demo controller showcasing different authentication methods.
 *
 * This controller demonstrates that Ogiri Security supports three authentication methods:
 * 1. HTTP Headers (access-token, client, uid, expiry)
 * 2. Secure Cookies (same fields as cookies)
 * 3. Bearer Token (Authorization: Bearer <base64-json>)
 *
 * All three methods are functionally equivalent and work with the same authentication filter.
 */
@RestController
@RequestMapping("/api/demo")
class DemoController {
  /**
   * Demonstrate header-based authentication.
   *
   * This endpoint shows authentication via HTTP headers. The client sends:
   * - access-token: The token value
   * - client: The client identifier
   * - uid: The user identifier
   * - expiry: Token expiration timestamp
   *
   * Example:
   * ```bash
   * curl http://localhost:8080/api/demo/headers \
   *   -H "access-token: <token>" \
   *   -H "client: <client>" \
   *   -H "uid: <uid>" \
   *   -H "expiry: <expiry>"
   * ```
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract headers
   * @return Authentication details and original headers
   */
  @GetMapping("/headers")
  fun demonstrateHeaderAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> {
    val headerInfo =
        mapOf(
            "method" to "HTTP Headers",
            "authenticated" to (authentication != null && authentication.isAuthenticated),
            "principal" to (authentication?.name ?: "anonymous"),
            "receivedHeaders" to
                mapOf(
                    "access-token" to request.getHeader("access-token"),
                    "client" to request.getHeader("client"),
                    "uid" to request.getHeader("uid"),
                    "expiry" to request.getHeader("expiry"),
                ),
        )
    return ResponseEntity.ok(headerInfo)
  }

  /**
   * Demonstrate cookie-based authentication.
   *
   * This endpoint shows authentication via secure cookies. The client sends cookies:
   * - access-token: The token value
   * - client: The client identifier
   * - uid: The user identifier
   * - expiry: Token expiration timestamp
   *
   * Cookies are automatically sent by the browser if set by the server with proper attributes
   * (HttpOnly, Secure, SameSite).
   *
   * Example:
   * ```bash
   * curl http://localhost:8080/api/demo/cookies \
   *   -b "access-token=<token>;client=<client>;uid=<uid>;expiry=<expiry>"
   * ```
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract cookies
   * @return Authentication details and received cookies
   */
  @GetMapping("/cookies")
  fun demonstrateCookieAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> {
    val cookies = request.cookies?.associate { it.name to it.value } ?: emptyMap()
    val cookieInfo =
        mapOf(
            "method" to "Secure Cookies",
            "authenticated" to (authentication != null && authentication.isAuthenticated),
            "principal" to (authentication?.name ?: "anonymous"),
            "receivedCookies" to
                mapOf(
                    "access-token" to cookies["access-token"],
                    "client" to cookies["client"],
                    "uid" to cookies["uid"],
                    "expiry" to cookies["expiry"],
                ),
        )
    return ResponseEntity.ok(cookieInfo)
  }

  /**
   * Demonstrate Bearer token authentication.
   *
   * This endpoint shows authentication via the Authorization header with a Bearer token. The token
   * is a Base64-encoded JSON object containing access-token, client, uid, and expiry.
   *
   * Example:
   * ```bash
   * # First, create the Bearer token (after login):
   * # Get the Authorization header value from login response
   *
   * curl http://localhost:8080/api/demo/bearer \
   *   -H "Authorization: Bearer <base64-encoded-json>"
   * ```
   *
   * The Bearer token format is:
   * ```
   * Authorization: Bearer eyJhY2Nlc3MtdG9rZW4iOiIuLi4iLCJjbGllbnQiOiIuLi4iLCJ1aWQiOiIuLi4iLCJleHBpcnkiOiIuLi4ifQ==
   * ```
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract Authorization header
   * @return Authentication details and Bearer token info
   */
  @GetMapping("/bearer")
  fun demonstrateBearerAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> {
    val authHeader = request.getHeader("Authorization")
    val bearerInfo =
        mapOf(
            "method" to "Bearer Token",
            "authenticated" to (authentication != null && authentication.isAuthenticated),
            "principal" to (authentication?.name ?: "anonymous"),
            "authorizationHeader" to (authHeader ?: "Not provided"),
            "note" to "Bearer token is Base64-encoded JSON with access-token, client, uid, expiry",
        )
    return ResponseEntity.ok(bearerInfo)
  }

  /**
   * General authentication info endpoint.
   *
   * This endpoint works with any authentication method (headers, cookies, or Bearer token). It
   * demonstrates that the authentication method is transparent to the application logic.
   *
   * Example:
   * ```bash
   * # Works with any auth method:
   * curl http://localhost:8080/api/demo/info -H "access-token: <token>" ...
   * curl http://localhost:8080/api/demo/info -b "access-token=<token>;..."
   * curl http://localhost:8080/api/demo/info -H "Authorization: Bearer <token>"
   * ```
   *
   * @param authentication Injected by Spring Security
   * @param request HTTP request for additional context
   * @return Current authentication state
   */
  @GetMapping("/info")
  fun getAuthInfo(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> {
    val authInfo =
        mapOf(
            "authenticated" to (authentication != null && authentication.isAuthenticated),
            "principal" to (authentication?.name ?: "anonymous"),
            "authorities" to
                (authentication?.authorities?.map { it.authority } ?: emptyList<String>()),
            "authMethod" to detectAuthMethod(request),
            "message" to
                "This endpoint accepts authentication via headers, cookies, or Bearer token",
        )
    return ResponseEntity.ok(authInfo)
  }

  /**
   * Detect which authentication method was used.
   *
   * @param request HTTP request
   * @return Detected authentication method
   */
  private fun detectAuthMethod(request: HttpServletRequest): String {
    return when {
      request.getHeader("Authorization")?.startsWith("Bearer ") == true -> "Bearer Token"
      request.cookies?.any { it.name == "access-token" } == true -> "Cookie"
      request.getHeader("access-token") != null -> "Header"
      else -> "None"
    }
  }
}
