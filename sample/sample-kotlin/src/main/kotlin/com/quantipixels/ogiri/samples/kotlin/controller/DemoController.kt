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

import com.quantipixels.ogiri.samples.kotlin.util.authBase
import com.quantipixels.ogiri.samples.kotlin.util.detectAuthMethod
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

  /** Shows authentication via HTTP headers (access-token, client, uid, expiry). */
  @GetMapping("/headers")
  fun demonstrateHeaderAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> =
      ResponseEntity.ok(
          authBase("HTTP Headers", authentication) +
              mapOf(
                  "receivedHeaders" to
                      mapOf(
                          "access-token" to request.getHeader("access-token"),
                          "client" to request.getHeader("client"),
                          "uid" to request.getHeader("uid"),
                          "expiry" to request.getHeader("expiry"),
                      ),
              ),
      )

  /** Shows authentication via secure cookies (same four fields set as HttpOnly cookies). */
  @GetMapping("/cookies")
  fun demonstrateCookieAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> {
    val cookies = request.cookies?.associate { it.name to it.value } ?: emptyMap()
    return ResponseEntity.ok(
        authBase("Secure Cookies", authentication) +
            mapOf(
                "receivedCookies" to
                    mapOf(
                        "access-token" to cookies["access-token"],
                        "client" to cookies["client"],
                        "uid" to cookies["uid"],
                        "expiry" to cookies["expiry"],
                    ),
            ),
    )
  }

  /** Shows authentication via Authorization: Bearer (Base64-encoded JSON). */
  @GetMapping("/bearer")
  fun demonstrateBearerAuth(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> =
      ResponseEntity.ok(
          authBase("Bearer Token", authentication) +
              mapOf(
                  "authorizationHeader" to (request.getHeader("Authorization") ?: "Not provided"),
                  "note" to
                      "Bearer token is Base64-encoded JSON with access-token, client, uid, expiry",
              ),
      )

  /** General info endpoint that works with any authentication method. */
  @GetMapping("/info")
  fun getAuthInfo(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, Any>> =
      ResponseEntity.ok(
          authBase(authentication) +
              mapOf(
                  "authorities" to
                      (authentication?.authorities?.map { it.authority } ?: emptyList<String>()),
                  "authMethod" to detectAuthMethod(request),
                  "message" to
                      "This endpoint accepts authentication via headers, cookies, or Bearer token",
              ),
      )
}
