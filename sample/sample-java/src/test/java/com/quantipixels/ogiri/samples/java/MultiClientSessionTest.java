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
import com.quantipixels.ogiri.samples.java.repository.SampleTokenRepository;
import com.quantipixels.ogiri.samples.java.service.SampleTokenService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MultiClientSessionTest {

  @Autowired private SampleTokenService tokenService;
  @Autowired private SampleTokenRepository tokenRepository;

  private static final Long TEST_USER_ID = 1L;

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
  }

  @Test
  void shouldSupportMultipleClientsPerUser() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");
    tokenService.createNewAuthToken(TEST_USER_ID, "web");
    tokenService.createNewAuthToken(TEST_USER_ID, "desktop");

    List<SampleToken> tokens = tokenRepository.findAllByUserId(TEST_USER_ID);

    assertEquals(3, tokens.size());

    Set<String> clients = tokens.stream().map(SampleToken::getClient).collect(Collectors.toSet());
    assertTrue(clients.contains("mobile"));
    assertTrue(clients.contains("web"));
    assertTrue(clients.contains("desktop"));
  }

  @Test
  void shouldAllowLogoutFromSingleClient() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");
    tokenService.createNewAuthToken(TEST_USER_ID, "web");
    tokenService.createNewAuthToken(TEST_USER_ID, "desktop");

    // Logout from mobile only
    tokenService.deleteToken(TEST_USER_ID, "mobile");

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(2, remaining.size());

    Set<String> clients =
        remaining.stream().map(SampleToken::getClient).collect(Collectors.toSet());
    assertFalse(clients.contains("mobile"));
    assertTrue(clients.contains("web"));
    assertTrue(clients.contains("desktop"));
  }

  @Test
  void shouldAllowLogoutFromAllClients() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");
    tokenService.createNewAuthToken(TEST_USER_ID, "web");
    tokenService.createNewAuthToken(TEST_USER_ID, "desktop");

    assertEquals(3, tokenRepository.findAllByUserId(TEST_USER_ID).size());

    // Logout from all clients
    tokenService.deleteAllForUser(TEST_USER_ID);

    assertEquals(0, tokenRepository.findAllByUserId(TEST_USER_ID).size());
  }

  @Test
  void shouldAllowBulkClientLogout() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");
    tokenService.createNewAuthToken(TEST_USER_ID, "web");
    tokenService.createNewAuthToken(TEST_USER_ID, "desktop");
    tokenService.createNewAuthToken(TEST_USER_ID, "tablet");

    // Bulk logout from mobile and web
    tokenService.deleteToken(TEST_USER_ID, List.of("mobile", "web"));

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(2, remaining.size());

    Set<String> clients =
        remaining.stream().map(SampleToken::getClient).collect(Collectors.toSet());
    assertTrue(clients.contains("desktop"));
    assertTrue(clients.contains("tablet"));
  }

  @Test
  void shouldIsolateTokensBetweenUsers() {
    Long user1 = 1L;
    Long user2 = 2L;

    tokenService.createNewAuthToken(user1, "mobile");
    tokenService.createNewAuthToken(user1, "web");
    tokenService.createNewAuthToken(user2, "mobile");

    assertEquals(2, tokenRepository.findAllByUserId(user1).size());
    assertEquals(1, tokenRepository.findAllByUserId(user2).size());

    // Deleting user1's tokens doesn't affect user2
    tokenService.deleteAllForUser(user1);

    assertEquals(0, tokenRepository.findAllByUserId(user1).size());
    assertEquals(1, tokenRepository.findAllByUserId(user2).size());
  }

  @Test
  void shouldUpdateExistingClientToken() {
    // Create initial token
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");

    SampleToken firstToken = tokenRepository.findByUserIdAndClient(TEST_USER_ID, "mobile");
    Long firstId = firstToken.getId();

    // Create token for same client again (should update)
    tokenService.createNewAuthToken(TEST_USER_ID, "mobile");

    // Should still have only one token for this client
    List<SampleToken> tokens =
        tokenRepository.findAllByUserId(TEST_USER_ID).stream()
            .filter(t -> "mobile".equals(t.getClient()))
            .toList();

    assertEquals(1, tokens.size());
  }

  @Test
  void shouldGetTokenByUserIdAndClient() {
    tokenService.createNewAuthToken(TEST_USER_ID, "specific-client");

    SampleToken token = tokenService.getByUserIdAndClient(TEST_USER_ID, "specific-client");

    assertNotNull(token);
    assertEquals("specific-client", token.getClient());
    assertEquals(TEST_USER_ID, token.getUserId());
  }

  @Test
  void shouldReturnNullForNonExistentClient() {
    SampleToken token = tokenService.getByUserIdAndClient(TEST_USER_ID, "nonexistent");

    assertNull(token);
  }
}
