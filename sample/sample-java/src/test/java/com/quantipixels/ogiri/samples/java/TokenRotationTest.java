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

import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import com.quantipixels.ogiri.samples.java.repository.SampleTokenJpaRepository;
import com.quantipixels.ogiri.samples.java.service.SampleTokenService;
import com.quantipixels.ogiri.security.core.AuthHeader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenRotationTest {

  @Autowired private SampleTokenService tokenService;
  @Autowired private SampleTokenJpaRepository tokenRepository;

  private static final Long TEST_USER_ID = 1L;
  private static final String TEST_USERNAME = "user1"; // Username for user ID 1
  private static final String TEST_CLIENT = "rotation-test-client";

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
  }

  @Test
  void shouldCreateNewAuthToken() {
    AuthHeader authHeader = tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);

    assertNotNull(authHeader);
    assertNotNull(authHeader.getAccessToken());
    assertEquals(TEST_CLIENT, authHeader.getClient());
    assertEquals(TEST_USERNAME, authHeader.getUid()); // UID is username, not numeric ID
    assertNotNull(authHeader.getExpiry());

    // Verify token persisted
    SampleToken savedToken = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNotNull(savedToken);
    assertEquals(TEST_USER_ID, savedToken.getUserId());
    assertEquals(TEST_CLIENT, savedToken.getClient());
  }

  @Test
  void shouldRotateTokenOnSubsequentCreation() {
    // Create first token
    AuthHeader firstAuth = tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);
    String firstToken = firstAuth.getAccessToken();

    // Simulate time passing beyond grace period
    SampleToken token = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    token.setLastUsedAt(Instant.now().minus(10, ChronoUnit.SECONDS));
    tokenRepository.save(token);
    tokenRepository.flush();

    // Create second token (rotation)
    AuthHeader secondAuth = tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);
    String secondToken = secondAuth.getAccessToken();

    // Tokens should be different
    assertNotEquals(firstToken, secondToken);
  }

  @Test
  void shouldPreserveLastTokenForGracePeriod() {
    // Create initial token
    tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);

    SampleToken token = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    String originalHash = token.getToken();

    // Simulate update that preserves last token
    token.setLastToken(originalHash);
    token.setToken("new-hash-value");
    tokenRepository.save(token);
    tokenRepository.flush();

    // Verify last token preserved
    SampleToken updated = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertEquals("new-hash-value", updated.getToken());
    assertEquals(originalHash, updated.getLastToken());
  }

  @Test
  void shouldSupportThreeTierGracePeriod() {
    // Create token with all three tiers
    AuthHeader auth = tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);

    SampleToken token = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    String currentHash = token.getToken();

    // Set up three-tier history
    token.setLastToken("previous-hash");
    token.setPreviousToken("oldest-hash");
    tokenRepository.save(token);
    tokenRepository.flush();

    SampleToken updated = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNotNull(updated.getToken());
    assertEquals("previous-hash", updated.getLastToken());
    assertEquals("oldest-hash", updated.getPreviousToken());
  }

  @Test
  void shouldDeleteTokenSuccessfully() {
    tokenService.createNewAuthToken(TEST_USER_ID, TEST_CLIENT);

    // Verify token exists
    assertNotNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT));

    // Delete
    tokenService.deleteToken(TEST_USER_ID, TEST_CLIENT);

    // Verify deleted
    assertNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT));
  }

  @Test
  void shouldDeleteAllTokensForUser() {
    // Create multiple tokens for same user
    tokenService.createNewAuthToken(TEST_USER_ID, "client-1");
    tokenService.createNewAuthToken(TEST_USER_ID, "client-2");
    tokenService.createNewAuthToken(TEST_USER_ID, "client-3");

    assertEquals(3, tokenRepository.findAllByUserId(TEST_USER_ID).size());

    // Delete all for user
    tokenService.deleteAllForUser(TEST_USER_ID);

    assertEquals(0, tokenRepository.findAllByUserId(TEST_USER_ID).size());
  }

  @Test
  void shouldGetAllTokensByUserId() {
    tokenService.createNewAuthToken(TEST_USER_ID, "client-a");
    tokenService.createNewAuthToken(TEST_USER_ID, "client-b");

    var tokens = tokenService.getAllByUserId(TEST_USER_ID);

    assertEquals(2, tokens.size());
  }

  @Test
  void shouldCleanupExpiredTokens() {
    // Create expired token directly
    SampleToken expiredToken = new SampleToken();
    expiredToken.setUserId(TEST_USER_ID);
    expiredToken.setClient("expired-client");
    expiredToken.setToken("hash");
    expiredToken.setExpiryAt(Instant.now().minus(1, ChronoUnit.HOURS));
    tokenRepository.save(expiredToken);

    // Create valid token
    tokenService.createNewAuthToken(TEST_USER_ID, "valid-client");

    // Run cleanup
    int deleted = tokenService.cleanupExpiredTokens(Instant.now());

    assertEquals(1, deleted);
    assertNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, "expired-client"));
    assertNotNull(tokenRepository.findByUserIdAndClient(TEST_USER_ID, "valid-client"));
  }
}
