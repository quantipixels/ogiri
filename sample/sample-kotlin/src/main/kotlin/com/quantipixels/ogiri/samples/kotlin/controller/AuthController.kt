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

import com.quantipixels.ogiri.security.core.SecurityServiceException
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Authentication controller demonstrating login and logout flows.
 *
 * This controller shows how to integrate with OgiriTokenService for authentication operations.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val tokenService: OgiriTokenService<*>,
    private val userDirectory: OgiriUserDirectory,
) {
  /**
   * Login endpoint that validates credentials and returns authentication tokens.
   *
   * On successful login, this endpoint:
   * 1. Validates username and password
   * 2. Creates authentication tokens (APP + sub-tokens)
   * 3. Returns tokens in response headers AND body
   * 4. Sets secure cookies if cookie config is enabled
   *
   * Example request:
   * ```bash
   * curl -X POST http://localhost:8080/api/auth/login \
   *   -H "Content-Type: application/json" \
   *   -d '{"username":"user1","password":"password"}'
   * ```
   *
   * Response includes:
   * - Headers: access-token, client, uid, expiry, Authorization (Bearer)
   * - Cookies: access-token, client, uid, expiry (if enabled)
   * - Body: JSON with token details
   *
   * @param request Login credentials
   * @param httpRequest HTTP request for context
   * @param httpResponse HTTP response for setting headers/cookies
   * @return Authentication response with token details
   */
  @PostMapping("/login")
  fun login(
      @RequestBody request: LoginRequest,
      httpRequest: HttpServletRequest,
      httpResponse: HttpServletResponse,
  ): ResponseEntity<AuthResponse> {
    return try {
      tokenService.verifyUser(
          httpRequest,
          httpResponse,
          request.username,
          request.password,
      )

      val accessToken = httpResponse.getHeader("access-token")
      val client = httpResponse.getHeader("client")
      val uid = httpResponse.getHeader("uid")
      val expiry = httpResponse.getHeader("expiry")

      ResponseEntity.ok(
          AuthResponse(
              accessToken = accessToken ?: "",
              client = client ?: "",
              uid = uid ?: "",
              expiry = expiry ?: "",
              message = "Login successful",
          ),
      )
    } catch (e: SecurityServiceException) {
      ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(
              AuthResponse(
                  accessToken = "",
                  client = "",
                  uid = "",
                  expiry = "",
                  message = "Invalid credentials",
              ),
          )
    }
  }

  /**
   * Logout endpoint that revokes the current authentication token.
   *
   * This endpoint:
   * 1. Extracts the current token from request headers/cookies
   * 2. Revokes the token and all associated sub-tokens
   * 3. Clears authentication cookies
   *
   * Example request:
   * ```bash
   * curl -X POST http://localhost:8080/api/auth/logout \
   *   -H "access-token: <token>" \
   *   -H "client: <client>" \
   *   -H "uid: <uid>" \
   *   -H "expiry: <expiry>"
   * ```
   *
   * @param authentication Current authentication context
   * @param httpRequest HTTP request for extracting token
   * @param httpResponse HTTP response for clearing cookies
   * @return Logout confirmation message
   */
  @PostMapping("/logout")
  fun logout(
      authentication: Authentication?,
      httpRequest: HttpServletRequest,
      httpResponse: HttpServletResponse,
  ): ResponseEntity<Map<String, String>> {
    if (authentication == null || !authentication.isAuthenticated) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(mapOf("message" to "Not authenticated"))
    }

    return try {
      val user = userDirectory.findByUsername(authentication.name)
      if (user != null) {
        tokenService.revokeClient(user.getOgiriUserId(), httpRequest, httpResponse)
      }
      ResponseEntity.ok(mapOf("message" to "Logout successful"))
    } catch (e: Exception) {
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(mapOf("message" to "Logout failed: ${e.message}"))
    }
  }
}

/** Login request body. */
data class LoginRequest(
    val username: String,
    val password: String,
)

/** Authentication response body. */
data class AuthResponse(
    val accessToken: String,
    val client: String,
    val uid: String,
    val expiry: String,
    val message: String,
)
