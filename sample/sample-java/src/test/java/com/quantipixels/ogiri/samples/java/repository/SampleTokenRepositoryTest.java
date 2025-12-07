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
    SampleToken token =
        new SampleToken(
            TEST_USER_ID, TEST_CLIENT, TEST_TOKEN, Instant.now().plus(1, ChronoUnit.HOURS));
    tokenRepository.save(token);

    SampleToken retrieved = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    assertNotNull(retrieved);
    assertEquals(TEST_USER_ID, retrieved.getUserId());
    assertEquals(TEST_CLIENT, retrieved.getClient());
    assertEquals(TEST_TOKEN, retrieved.getToken());
  }

  @Test
  void shouldReturnNullForNonExistentToken() {
    SampleToken retrieved = tokenRepository.findByUserIdAndClient(999L, "non-existent");
    assertNull(retrieved);
  }

  @Test
  void shouldFindAllTokensForUserOrderedByUpdatedAtDesc() throws InterruptedException {
    SampleToken token1 =
        new SampleToken(
            TEST_USER_ID, "client-1", "token-1", Instant.now().plus(1, ChronoUnit.HOURS));
    SampleToken token2 =
        new SampleToken(
            TEST_USER_ID, "client-2", "token-2", Instant.now().plus(2, ChronoUnit.HOURS));

    tokenRepository.save(token1);
    tokenRepository.flush();
    Thread.sleep(10);
    tokenRepository.save(token2);

    List<SampleToken> tokens = tokenRepository.findAllByUserId(TEST_USER_ID);

    assertEquals(2, tokens.size());
    assertEquals("client-2", tokens.get(0).getClient());
    assertEquals("client-1", tokens.get(1).getClient());
  }

  @Test
  void shouldFindExpiredTokens() {
    Instant now = Instant.now();

    SampleToken expiredToken =
        new SampleToken(TEST_USER_ID, "expired-client", TEST_TOKEN, now.minus(1, ChronoUnit.HOURS));
    SampleToken validToken =
        new SampleToken(TEST_USER_ID, "valid-client", TEST_TOKEN, now.plus(1, ChronoUnit.HOURS));

    tokenRepository.save(expiredToken);
    tokenRepository.save(validToken);

    List<SampleToken> expired = tokenRepository.findByExpiryAtBefore(now);

    assertEquals(1, expired.size());
    assertEquals("expired-client", expired.get(0).getClient());
  }

  @Test
  void shouldDeleteTokenByUserAndClient() {
    SampleToken token =
        new SampleToken(
            TEST_USER_ID, TEST_CLIENT, TEST_TOKEN, Instant.now().plus(1, ChronoUnit.HOURS));
    tokenRepository.save(token);

    tokenRepository.deleteByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    SampleToken retrieved = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNull(retrieved);
  }

  @Test
  void shouldDeleteMultipleTokensByClientList() {
    SampleToken token1 =
        new SampleToken(
            TEST_USER_ID, "client-1", "token-1", Instant.now().plus(1, ChronoUnit.HOURS));
    SampleToken token2 =
        new SampleToken(
            TEST_USER_ID, "client-2", "token-2", Instant.now().plus(1, ChronoUnit.HOURS));
    SampleToken token3 =
        new SampleToken(
            TEST_USER_ID, "client-3", "token-3", Instant.now().plus(1, ChronoUnit.HOURS));

    tokenRepository.save(token1);
    tokenRepository.save(token2);
    tokenRepository.save(token3);

    tokenRepository.deleteByUserIdAndClientIn(TEST_USER_ID, List.of("client-1", "client-2"));

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(1, remaining.size());
    assertEquals("client-3", remaining.get(0).getClient());
  }

  @Test
  void shouldDeleteAllTokensForUser() {
    SampleToken token1 =
        new SampleToken(
            TEST_USER_ID, "client-1", "token-1", Instant.now().plus(1, ChronoUnit.HOURS));
    SampleToken token2 =
        new SampleToken(
            TEST_USER_ID, "client-2", "token-2", Instant.now().plus(1, ChronoUnit.HOURS));

    tokenRepository.save(token1);
    tokenRepository.save(token2);

    tokenRepository.deleteByUserId(TEST_USER_ID);

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertTrue(remaining.isEmpty());
  }

  @Test
  void shouldDeleteTokensFromCollection() {
    SampleToken token1 =
        new SampleToken(
            TEST_USER_ID, "client-1", "token-1", Instant.now().plus(1, ChronoUnit.HOURS));
    SampleToken token2 =
        new SampleToken(
            TEST_USER_ID, "client-2", "token-2", Instant.now().plus(1, ChronoUnit.HOURS));

    SampleToken saved1 = tokenRepository.save(token1);
    tokenRepository.save(token2);

    tokenRepository.deleteAll(List.of(saved1));

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(1, remaining.size());
    assertEquals("client-2", remaining.get(0).getClient());
  }

  @Test
  void shouldUpdateTokenProperties() {
    SampleToken token =
        new SampleToken(
            TEST_USER_ID, TEST_CLIENT, TEST_TOKEN, Instant.now().plus(1, ChronoUnit.HOURS));
    token = tokenRepository.save(token);

    token.setToken("new-hashed-token");
    token.setLastUsedAt(Instant.now());
    tokenRepository.save(token);

    SampleToken updated = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNotNull(updated);
    assertEquals("new-hashed-token", updated.getToken());
    assertNotNull(updated.getLastUsedAt());
  }
}
