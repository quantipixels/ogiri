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

import com.quantipixels.ogiri.samples.kotlin.repository.SampleTokenRepository
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only endpoints for exercising expiration flows in the sample app.
 *
 * Not for production use. Exposes helpers that manipulate token state directly so the frontend can
 * drive the full expiry → 401 → redirect cycle without waiting for a real TTL to elapse.
 */
@RestController
@RequestMapping("/api/test")
@Profile("!jdbc")
class TestController(
    private val tokenRepository: SampleTokenRepository,
    private val userDirectory: OgiriUserDirectory,
) {

  /**
   * Backdates the current session's
   * [expiryAt][com.quantipixels.ogiri.jpa.OgiriBaseTokenEntity.expiryAt] to one hour in the past so
   * the next authenticated request returns 401.
   *
   * Requires a valid session (Ogiri filter must authenticate the request before this method runs).
   * The `client` header identifies the session to expire.
   */
  @PostMapping("/expire-token")
  fun expireToken(
      authentication: Authentication?,
      request: HttpServletRequest,
  ): ResponseEntity<Map<String, String>> {
    if (authentication == null || !authentication.isAuthenticated) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(mapOf("message" to "Not authenticated"))
    }

    val user =
        userDirectory.findByUsername(authentication.name)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("message" to "User not found"))

    val client =
        request.getHeader("client")?.takeIf { it.isNotBlank() }
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Missing client header"))

    val entity =
        tokenRepository.findByUserIdAndClient(user.getOgiriUserId(), client).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("message" to "Session not found"))

    entity.expiryAt = Instant.now().minusSeconds(3600)
    tokenRepository.save(entity)

    return ResponseEntity.ok(mapOf("message" to "Token expired"))
  }
}
