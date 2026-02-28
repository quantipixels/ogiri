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
package com.quantipixels.ogiri.samples.kotlin

import com.quantipixels.ogiri.samples.kotlin.repository.SampleTokenRepository
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles

/**
 * Full HTTP cycle test — Kotlin sample app.
 *
 * Starts a real server on a random port and drives the complete auth cycle via TestRestTemplate:
 * login → rotation → stale-token rejection → logout → post-logout rejection.
 *
 * Configuration overrides:
 * - register-filter=true re-enables the Ogiri security filter (disabled in the base test profile)
 * - rotate-stale-seconds=0 forces rotation on every request (skips the shouldRotate() guard)
 * - batch-grace-seconds=0 disables the batch window so rotation is never suppressed
 * - cookies.enabled=false tests header-only auth; avoids cookie complexity over plain HTTP
 *
 * Note: @Transactional does not roll back across RANDOM_PORT tests because the HTTP server runs in
 * a separate thread. Cleanup is performed in @BeforeEach instead.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties =
        [
            "ogiri.security.register-filter=true",
            "ogiri.auth.rotate-stale-seconds=0",
            "ogiri.auth.batch-grace-seconds=0",
            "ogiri.cookies.enabled=false",
        ],
)
@ActiveProfiles("test")
class FullCycleHttpTest {

  @Autowired lateinit var rest: TestRestTemplate
  @Autowired lateinit var tokenRepository: SampleTokenRepository

  @BeforeEach fun clean() = tokenRepository.deleteAll()

  @Test
  fun `full auth cycle - login, rotation, stale rejection, logout`() {
    // ── 1. Login ──────────────────────────────────────────────────────────────────────
    val loginBody = mapOf("username" to "user1@example.com", "password" to "password")
    val loginResponse = rest.postForEntity("/api/auth/login", loginBody, Map::class.java)
    assertEquals(200, loginResponse.statusCode.value(), "Login should succeed")

    val token0 =
        requireNotNull(loginResponse.headers.getFirst("access-token")) {
          "Login must return access-token header"
        }
    val client =
        requireNotNull(loginResponse.headers.getFirst("client")) {
          "Login must return client header"
        }
    val uid =
        requireNotNull(loginResponse.headers.getFirst("uid")) { "Login must return uid header" }
    val expiry =
        requireNotNull(loginResponse.headers.getFirst("expiry")) {
          "Login must return expiry header"
        }

    // ── 2. First authenticated request — filter rotates the token ─────────────────
    val r1 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(token0, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(200, r1.statusCode.value(), "First authenticated request should succeed")

    val token1 =
        requireNotNull(r1.headers.getFirst("access-token")) {
          "Filter must return rotated token in response headers"
        }
    assertNotEquals(token0, token1, "Rotated token must differ from the login token")

    // ── 3. Original token is stale — must be rejected ─────────────────────────────
    val stale =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(token0, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(401, stale.statusCode.value(), "Stale token must be rejected after rotation")

    // ── 4. Rotated token is accepted and produces another rotation ────────────────
    val r2 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(token1, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(200, r2.statusCode.value(), "Rotated token should be accepted")
    val token2 = requireNotNull(r2.headers.getFirst("access-token"))

    // ── 5. Logout with the current token ──────────────────────────────────────────
    val logout =
        rest.exchange(
            "/api/auth/logout",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders(token2, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(200, logout.statusCode.value(), "Logout should succeed")

    // ── 6. Session is fully invalidated — any token rejected ──────────────────────
    val postLogout =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(token2, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(401, postLogout.statusCode.value(), "Session must be invalidated after logout")
  }

  @Test
  fun `expired token is rejected`() {
    // ── 1. Login ──────────────────────────────────────────────────────────────────────
    val loginBody = mapOf("username" to "user1@example.com", "password" to "password")
    val loginResponse = rest.postForEntity("/api/auth/login", loginBody, Map::class.java)
    assertEquals(200, loginResponse.statusCode.value(), "Login should succeed")

    val token0 =
        requireNotNull(loginResponse.headers.getFirst("access-token")) {
          "Login must return access-token header"
        }
    val client =
        requireNotNull(loginResponse.headers.getFirst("client")) {
          "Login must return client header"
        }
    val uid =
        requireNotNull(loginResponse.headers.getFirst("uid")) { "Login must return uid header" }
    val expiry =
        requireNotNull(loginResponse.headers.getFirst("expiry")) {
          "Login must return expiry header"
        }

    // ── 2. Confirm the token is accepted before expiry ────────────────────────────
    val r1 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(token0, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(200, r1.statusCode.value(), "Request before expiry should succeed")

    // The filter rotates the token on every request (rotate-stale-seconds=0), so use
    // the rotated token for subsequent requests.
    val currentToken = r1.headers.getFirst("access-token") ?: token0

    // ── 3. Backdate the token's expiryAt in the DB ────────────────────────────────
    val entity =
        tokenRepository.findByUserIdAndClient(uid.toLong(), client).orElseThrow {
          AssertionError("Session must exist in the DB")
        }
    entity.expiryAt = Instant.now().minusSeconds(3600)
    tokenRepository.save(entity)

    // ── 4. Expired token must be rejected with 401 ────────────────────────────────
    val expired =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(currentToken, client, uid, expiry)),
            Map::class.java,
        )
    assertEquals(401, expired.statusCode.value(), "Expired token must be rejected")
  }

  // ── helpers ───────────────────────────────────────────────────────────────────────────

  private fun authHeaders(accessToken: String, client: String, uid: String, expiry: String) =
      HttpHeaders().apply {
        set("access-token", accessToken)
        set("client", client)
        set("uid", uid)
        set("expiry", expiry)
      }
}
