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
package com.quantipixels.ogiri.samples.java.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import com.quantipixels.ogiri.samples.java.Application;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("jdbc")
class JdbcSampleTokenRepositoryTest {

  @Autowired private JdbcSampleTokenRepository tokenRepository;

  private static final long TEST_USER_ID = 100L;
  private static final String TEST_CLIENT = "test-client";
  private static final String TEST_TOKEN = "hashed-token-123";

  @BeforeEach
  void setUp() {
    tokenRepository.deleteByUserId(TEST_USER_ID);
    tokenRepository.deleteByUserId(101L);
    tokenRepository.deleteByUserId(102L);
  }

  @Test
  void shouldSaveAndRetrieveTokenByUserAndClient() {
    JdbcSampleToken token = createToken(TEST_USER_ID, TEST_CLIENT, TEST_TOKEN);
    tokenRepository.save(token);

    Optional<JdbcSampleToken> retrieved =
        tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    assertTrue(retrieved.isPresent());
    assertEquals(TEST_USER_ID, retrieved.get().getUserId());
    assertEquals(TEST_CLIENT, retrieved.get().getClient());
    assertEquals(TEST_TOKEN, retrieved.get().getToken());
  }

  @Test
  void shouldReturnEmptyOptionalForNonExistentToken() {
    Optional<JdbcSampleToken> retrieved = tokenRepository.findByUserIdAndClient(999L, "none");
    assertFalse(retrieved.isPresent());
  }

  @Test
  void shouldFindAllTokensForUserAndExcludeOtherUsers() {
    tokenRepository.save(createToken(TEST_USER_ID, "client-1", "token-1"));
    tokenRepository.save(createToken(TEST_USER_ID, "client-2", "token-2"));
    tokenRepository.save(createToken(101L, "client-3", "token-3"));

    List<JdbcSampleToken> tokens = tokenRepository.findByUserIdOrderByUpdatedAtDesc(TEST_USER_ID);

    assertEquals(2, tokens.size());
    List<String> clients = tokens.stream().map(JdbcSampleToken::getClient).toList();
    assertTrue(clients.contains("client-1"));
    assertTrue(clients.contains("client-2"));
  }

  @Test
  void shouldFindExpiredTokens() {
    Instant now = Instant.now();

    JdbcSampleToken expired = createToken(TEST_USER_ID, "expired-client", TEST_TOKEN);
    expired.setExpiryAt(now.minus(1, ChronoUnit.HOURS));

    JdbcSampleToken valid = createToken(TEST_USER_ID, "valid-client", TEST_TOKEN);
    valid.setExpiryAt(now.plus(1, ChronoUnit.HOURS));

    tokenRepository.save(expired);
    tokenRepository.save(valid);

    List<JdbcSampleToken> expiredTokens = tokenRepository.findByExpiryAtBefore(now);

    assertTrue(expiredTokens.stream().anyMatch(t -> t.getClient().equals("expired-client")));
    assertTrue(expiredTokens.stream().noneMatch(t -> t.getExpiryAt().isAfter(now)));
  }

  @Test
  void shouldDeleteTokenByUserAndClient() {
    tokenRepository.save(createToken(TEST_USER_ID, TEST_CLIENT, TEST_TOKEN));

    tokenRepository.deleteByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);

    assertFalse(tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT).isPresent());
  }

  @Test
  void shouldDeleteSelectedTokensByClientList() {
    tokenRepository.save(createToken(102L, "client-1", "token-1"));
    tokenRepository.save(createToken(102L, "client-2", "token-2"));
    tokenRepository.save(createToken(102L, "client-3", "token-3"));

    tokenRepository.deleteByUserIdAndClientIn(102L, List.of("client-1", "client-2"));

    List<JdbcSampleToken> remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(102L);
    assertEquals(1, remaining.size());
    assertEquals("client-3", remaining.get(0).getClient());
  }

  private JdbcSampleToken createToken(long userId, String client, String tokenHash) {
    JdbcSampleToken token = new JdbcSampleToken();
    token.setUserId(userId);
    token.setClient(client);
    token.setToken(tokenHash);
    token.setExpiryAt(Instant.now().plus(1, ChronoUnit.HOURS));
    return token;
  }
}
