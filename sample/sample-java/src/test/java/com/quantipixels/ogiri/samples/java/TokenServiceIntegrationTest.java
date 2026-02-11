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

import static com.quantipixels.ogiri.security.core.AuthHeaderKt.ACCESS_TOKEN;
import static com.quantipixels.ogiri.security.core.AuthHeaderKt.CLIENT;
import static com.quantipixels.ogiri.security.core.AuthHeaderKt.EXPIRY;
import static com.quantipixels.ogiri.security.core.AuthHeaderKt.UID;
import static org.junit.jupiter.api.Assertions.*;

import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import com.quantipixels.ogiri.samples.java.repository.SampleTokenRepository;
import com.quantipixels.ogiri.samples.java.service.SampleTokenService;
import com.quantipixels.ogiri.security.core.SecurityServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenServiceIntegrationTest {

  @Autowired private SampleTokenService tokenService;
  @Autowired private SampleTokenRepository tokenRepository;

  private static final Long TEST_USER_ID = 1L;
  private static final String TEST_EMAIL = "user1@example.com";
  private static final String TEST_PASSWORD = "password";

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
    SecurityContextHolder.clearContext();
  }

  @Test
  void createNewAuthToken_withNullClient_generatesClientAndPersistsAppToken() {
    var authHeader = tokenService.createNewAuthToken(TEST_USER_ID, null, null);

    assertNotNull(authHeader.getAccessToken());
    assertNotNull(authHeader.getClient());
    assertEquals("user1", authHeader.getUid());

    var savedToken =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, authHeader.getClient()).orElse(null);
    assertNotNull(savedToken);
    assertEquals(TEST_USER_ID, savedToken.getUserId());
    assertEquals(authHeader.getClient(), savedToken.getClient());
    assertEquals("APP", savedToken.getTokenType());
  }

  @Test
  void verifyUser_authenticatesAndAppendsAuthHeaders() {
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setRemoteAddr("127.0.0.1");
    var response = new MockHttpServletResponse();

    tokenService.verifyUser(request, response, TEST_EMAIL, TEST_PASSWORD);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertNotNull(authentication);
    assertEquals("user1", authentication.getName());
    assertNotNull(response.getHeader(ACCESS_TOKEN));
    assertNotNull(response.getHeader(CLIENT));
    assertEquals("user1", response.getHeader(UID));
    assertNotNull(response.getHeader(EXPIRY));
  }

  @Test
  void verifyUser_rejectsInvalidCredentialsWithoutCreatingAuthContext() {
    var request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setRemoteAddr("127.0.0.1");
    var response = new MockHttpServletResponse();

    assertThrows(
        SecurityServiceException.class,
        () -> tokenService.verifyUser(request, response, TEST_EMAIL, "wrong-password"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(0, tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID).size());
  }

  @Test
  void createNewAuthToken_rotatesTokenForSameClientWhileKeepingSinglePersistedRow() {
    var first = tokenService.createNewAuthToken(TEST_USER_ID, "web", null);
    var second = tokenService.createNewAuthToken(TEST_USER_ID, "web", null);

    assertNotEquals(first.getAccessToken(), second.getAccessToken());
    var webTokens =
        tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID).stream()
            .filter(token -> "web".equals(token.getClient()))
            .toList();
    assertEquals(1, webTokens.size());
  }

  @Test
  void deleteToken_removesOnlyTargetedClientTokenForSameUser() {
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile", null);
    tokenService.createNewAuthToken(TEST_USER_ID, "web", null);

    tokenService.deleteToken(TEST_USER_ID, "mobile");

    assertNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, "mobile").orElse(null));
    assertNotNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, "web").orElse(null));
  }

  @Test
  void revokeClient_removesTokenForClientRepresentedByHeaders() {
    var issued = tokenService.createNewAuthToken(TEST_USER_ID, "mobile", null);
    SampleToken savedToken =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, "mobile").orElse(null);
    assertNotNull(savedToken);

    var request = new MockHttpServletRequest("POST", "/api/auth/logout");
    request.addHeader(ACCESS_TOKEN, issued.getAccessToken());
    request.addHeader(CLIENT, issued.getClient());
    request.addHeader(UID, issued.getUid());
    request.addHeader(EXPIRY, issued.getExpiry());
    var response = new MockHttpServletResponse();

    tokenService.revokeClient(TEST_USER_ID, request, response);

    assertNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, "mobile").orElse(null));
    assertEquals(issued.getAccessToken(), response.getHeader(ACCESS_TOKEN));
    assertEquals("mobile", response.getHeader(CLIENT));
  }
}
