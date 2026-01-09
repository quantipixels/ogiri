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
package com.quantipixels.ogiri.samples.java.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.quantipixels.ogiri.samples.java.Application;
import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class SampleTokenRepositoryTest {

  @Autowired private SampleTokenRepository tokenRepository;

  private static final Long TEST_USER_ID = 1L;
  private static final String TEST_CLIENT = "test-client";
  private static final String TEST_TOKEN = "hashed-token-123";

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
  }

  @Test
  void shouldSaveAndRetrieveTokenByUserAndClient() {
    SampleToken token = createToken(TEST_USER_ID, TEST_CLIENT, TEST_TOKEN);
    tokenRepository.save(token);

    Optional<SampleToken> retrieved =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    assertTrue(retrieved.isPresent());
    assertEquals(TEST_USER_ID, retrieved.get().getUserId());
    assertEquals(TEST_CLIENT, retrieved.get().getClient());
    assertEquals(TEST_TOKEN, retrieved.get().getToken());
  }

  @Test
  void shouldReturnEmptyOptionalForNonExistentToken() {
    Optional<SampleToken> retrieved = tokenRepository.findByUserIdAndClient(999L, "non-existent");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void shouldFindAllTokensForUserOrderedByUpdatedAtDesc() throws InterruptedException {
    SampleToken token1 = createToken(TEST_USER_ID, "client-1", "token-1");
    SampleToken token2 = createToken(TEST_USER_ID, "client-2", "token-2");

    tokenRepository.save(token1);
    tokenRepository.flush();
    Thread.sleep(100); // Ensure different timestamps
    tokenRepository.save(token2);

    List<SampleToken> tokens = tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID);

    assertEquals(2, tokens.size());
    List<String> clients = tokens.stream().map(SampleToken::getClient).toList();
    assertTrue(clients.contains("client-1"));
    assertTrue(clients.contains("client-2"));
  }

  @Test
  void shouldFindExpiredTokens() {
    Instant now = Instant.now();

    SampleToken expiredToken = createToken(TEST_USER_ID, "expired-client", TEST_TOKEN);
    expiredToken.setExpiryAt(now.minus(1, ChronoUnit.HOURS));

    SampleToken validToken = createToken(TEST_USER_ID, "valid-client", TEST_TOKEN);
    validToken.setExpiryAt(now.plus(1, ChronoUnit.HOURS));

    tokenRepository.save(expiredToken);
    tokenRepository.save(validToken);

    List<SampleToken> expired = tokenRepository.findByExpiryAtBefore(now);

    assertEquals(1, expired.size());
    assertEquals("expired-client", expired.get(0).getClient());
  }

  @Test
  void shouldDeleteTokenByUserAndClient() {
    SampleToken token = createToken(TEST_USER_ID, TEST_CLIENT, TEST_TOKEN);
    tokenRepository.save(token);

    tokenRepository.deleteByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    Optional<SampleToken> retrieved =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertFalse(retrieved.isPresent());
  }

  @Test
  void shouldDeleteMultipleTokensByClientList() {
    SampleToken token1 = createToken(TEST_USER_ID, "client-1", "token-1");
    SampleToken token2 = createToken(TEST_USER_ID, "client-2", "token-2");
    SampleToken token3 = createToken(TEST_USER_ID, "client-3", "token-3");

    tokenRepository.save(token1);
    tokenRepository.save(token2);
    tokenRepository.save(token3);

    tokenRepository.deleteByUserIdAndClientIn(TEST_USER_ID, List.of("client-1", "client-2"));

    List<SampleToken> remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID);
    assertEquals(1, remaining.size());
    assertEquals("client-3", remaining.get(0).getClient());
  }

  @Test
  void shouldDeleteAllTokensForUser() {
    SampleToken token1 = createToken(TEST_USER_ID, "client-1", "token-1");
    SampleToken token2 = createToken(TEST_USER_ID, "client-2", "token-2");

    tokenRepository.save(token1);
    tokenRepository.save(token2);

    tokenRepository.deleteByUserId(TEST_USER_ID);

    List<SampleToken> remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID);
    assertTrue(remaining.isEmpty());
  }

  @Test
  void shouldDeleteTokensFromCollection() {
    SampleToken token1 = createToken(TEST_USER_ID, "client-1", "token-1");
    SampleToken token2 = createToken(TEST_USER_ID, "client-2", "token-2");

    SampleToken saved1 = tokenRepository.save(token1);
    tokenRepository.save(token2);

    tokenRepository.delete(saved1);

    List<SampleToken> remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID);
    assertEquals(1, remaining.size());
    assertEquals("client-2", remaining.get(0).getClient());
  }

  @Test
  void shouldUpdateTokenProperties() {
    SampleToken token = createToken(TEST_USER_ID, TEST_CLIENT, TEST_TOKEN);
    token = tokenRepository.save(token);

    token.setToken("new-hashed-token");
    token.setLastUsedAt(Instant.now());
    tokenRepository.save(token);

    Optional<SampleToken> updated =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertTrue(updated.isPresent());
    assertEquals("new-hashed-token", updated.get().getToken());
    assertNotNull(updated.get().getLastUsedAt());
  }

  private SampleToken createToken(Long userId, String client, String token) {
    SampleToken sampleToken = new SampleToken();
    sampleToken.setUserId(userId);
    sampleToken.setClient(client);
    sampleToken.setToken(token);
    sampleToken.setExpiryAt(Instant.now().plus(1, ChronoUnit.HOURS));
    return sampleToken;
  }
}
