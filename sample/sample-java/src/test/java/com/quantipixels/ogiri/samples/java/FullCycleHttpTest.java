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
package com.quantipixels.ogiri.samples.java;

import static org.junit.jupiter.api.Assertions.*;

import com.quantipixels.ogiri.samples.java.repository.SampleTokenRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full HTTP cycle test — Java sample app.
 *
 * <p>Starts a real server on a random port and drives the complete auth cycle via TestRestTemplate:
 * login → rotation → stale-token rejection → logout → post-logout rejection.
 *
 * <p>Configuration overrides:
 *
 * <ul>
 *   <li>register-filter=true — re-enables the Ogiri security filter (disabled in the base test
 *       profile)
 *   <li>rotate-stale-seconds=0 — forces rotation on every request (skips the shouldRotate() guard)
 *   <li>batch-grace-seconds=0 — disables the batch window so rotation is never suppressed
 *   <li>cookies.enabled=false — tests header-only auth; avoids cookie complexity over plain HTTP
 * </ul>
 *
 * <p>Note: {@code @Transactional} does not roll back across RANDOM_PORT tests because the HTTP
 * server runs in a separate thread. Cleanup is performed in {@code @BeforeEach} instead.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "ogiri.security.register-filter=true",
      "ogiri.auth.rotate-stale-seconds=0",
      "ogiri.auth.batch-grace-seconds=0",
      "ogiri.cookies.enabled=false",
    })
@ActiveProfiles("test")
class FullCycleHttpTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private SampleTokenRepository tokenRepository;

  @BeforeEach
  void clean() {
    tokenRepository.deleteAll();
  }

  @Test
  void fullAuthCycle_login_rotation_staleRejection_logout() {
    // ── 1. Login ──────────────────────────────────────────────────────────────────────
    var loginBody = Map.of("username", "user1@example.com", "password", "password");
    var loginResponse = rest.postForEntity("/api/auth/login", loginBody, Map.class);
    assertEquals(200, loginResponse.getStatusCode().value(), "Login should succeed");

    var token0 = loginResponse.getHeaders().getFirst("access-token");
    var client = loginResponse.getHeaders().getFirst("client");
    var uid = loginResponse.getHeaders().getFirst("uid");
    var expiry = loginResponse.getHeaders().getFirst("expiry");
    assertNotNull(token0, "Login must return access-token header");
    assertNotNull(client, "Login must return client header");
    assertNotNull(uid, "Login must return uid header");
    assertNotNull(expiry, "Login must return expiry header");

    // ── 2. First authenticated request — filter rotates the token ─────────────────
    var r1 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token0, client, uid, expiry)),
            Map.class);
    assertEquals(200, r1.getStatusCode().value(), "First authenticated request should succeed");

    var token1 = r1.getHeaders().getFirst("access-token");
    assertNotNull(token1, "Filter must return rotated token in response headers");
    assertNotEquals(token0, token1, "Rotated token must differ from the login token");

    // ── 3. Original token is stale — must be rejected ─────────────────────────────
    var stale =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token0, client, uid, expiry)),
            Map.class);
    assertEquals(401, stale.getStatusCode().value(), "Stale token must be rejected after rotation");

    // ── 4. Rotated token is accepted and produces another rotation ────────────────
    var r2 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token1, client, uid, expiry)),
            Map.class);
    assertEquals(200, r2.getStatusCode().value(), "Rotated token should be accepted");
    var token2 = r2.getHeaders().getFirst("access-token");
    assertNotNull(token2);

    // ── 5. Logout with the current token ──────────────────────────────────────────
    var logout =
        rest.exchange(
            "/api/auth/logout",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(token2, client, uid, expiry)),
            Map.class);
    assertEquals(200, logout.getStatusCode().value(), "Logout should succeed");

    // ── 6. Session is fully invalidated — any token rejected ──────────────────────
    var postLogout =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token2, client, uid, expiry)),
            Map.class);
    assertEquals(
        401, postLogout.getStatusCode().value(), "Session must be invalidated after logout");
  }

  @Test
  void expiredToken_isRejected() {
    // ── 1. Login ──────────────────────────────────────────────────────────────────────
    var loginBody = Map.of("username", "user1@example.com", "password", "password");
    var loginResponse = rest.postForEntity("/api/auth/login", loginBody, Map.class);
    assertEquals(200, loginResponse.getStatusCode().value(), "Login should succeed");

    var token0 = loginResponse.getHeaders().getFirst("access-token");
    var client = loginResponse.getHeaders().getFirst("client");
    var uid = loginResponse.getHeaders().getFirst("uid");
    var expiry = loginResponse.getHeaders().getFirst("expiry");
    assertNotNull(token0, "Login must return access-token header");
    assertNotNull(client, "Login must return client header");
    assertNotNull(uid, "Login must return uid header");
    assertNotNull(expiry, "Login must return expiry header");

    // ── 2. Confirm the token is accepted before expiry ────────────────────────────
    var r1 =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token0, client, uid, expiry)),
            Map.class);
    assertEquals(200, r1.getStatusCode().value(), "Request before expiry should succeed");

    // The filter rotates the token on every request (rotate-stale-seconds=0), so use
    // the rotated token for subsequent requests.
    var currentToken =
        r1.getHeaders().getFirst("access-token") != null
            ? r1.getHeaders().getFirst("access-token")
            : token0;

    // ── 3. Backdate the token's expiryAt in the DB ────────────────────────────────
    var sessionToken = tokenRepository.findByUserIdAndClient(Long.parseLong(uid), client);
    assertTrue(sessionToken.isPresent(), "Session must exist in the DB");
    var entity = sessionToken.get();
    entity.setExpiryAt(Instant.now().minusSeconds(3600));
    tokenRepository.save(entity);

    // ── 4. Expired token must be rejected with 401 ────────────────────────────────
    var expired =
        rest.exchange(
            "/api/demo/info",
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(currentToken, client, uid, expiry)),
            Map.class);
    assertEquals(401, expired.getStatusCode().value(), "Expired token must be rejected");
  }

  // ── helpers ──────────────────────────────────────────────────────────────────────────────

  private HttpHeaders authHeaders(String accessToken, String client, String uid, String expiry) {
    var headers = new HttpHeaders();
    headers.set("access-token", accessToken);
    headers.set("client", client);
    headers.set("uid", uid);
    headers.set("expiry", expiry);
    return headers;
  }
}
