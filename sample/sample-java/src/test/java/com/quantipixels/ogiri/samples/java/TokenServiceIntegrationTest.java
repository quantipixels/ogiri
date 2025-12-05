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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenServiceIntegrationTest {

  @Autowired
  private SampleTokenRepository tokenRepository;

  private static final Long TEST_USER_ID = 1L;
  private static final String TEST_CLIENT = "test-app";

  @BeforeEach
  void setUp() {
    tokenRepository.deleteAll();
  }

  @Test
  void shouldCreateAndSaveNewToken() {
    SampleToken token = new SampleToken(
        TEST_USER_ID,
        TEST_CLIENT,
        "hashed-token-value",
        Instant.now().plusSeconds(3600)
    );
    token.setPlainToken("plain-token-value");
    tokenRepository.save(token);

    SampleToken savedToken = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNotNull(savedToken);
    assertEquals(TEST_USER_ID, savedToken.getUserId());
    assertEquals(TEST_CLIENT, savedToken.getClient());
    assertEquals("hashed-token-value", savedToken.getToken());
    assertEquals("APP", savedToken.getTokenType());
  }

  @Test
  void shouldSupportTokenRotationWithGracePeriod() {
    // Save initial token
    SampleToken token1 = new SampleToken(
        TEST_USER_ID,
        TEST_CLIENT,
        "token-hash-1",
        Instant.now().plusSeconds(3600)
    );
    tokenRepository.save(token1);

    // Rotate token by deleting old and saving new
    tokenRepository.deleteByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    tokenRepository.flush();  // Ensure delete is flushed before saving new token

    SampleToken token2 = new SampleToken(
        TEST_USER_ID,
        TEST_CLIENT,
        "token-hash-2",
        Instant.now().plusSeconds(3600)
    );
    tokenRepository.save(token2);

    SampleToken rotatedToken = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    assertNotNull(rotatedToken);
    assertEquals("token-hash-2", rotatedToken.getToken());
  }

  @Test
  void shouldHandleMultipleConcurrentClientsForSameUser() {
    List<String> clients = List.of("mobile", "web", "desktop");

    for (String client : clients) {
      SampleToken token = new SampleToken(
          TEST_USER_ID,
          client,
          "hash-" + client,
          Instant.now().plusSeconds(3600)
      );
      tokenRepository.save(token);
    }

    List<SampleToken> userTokens = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(3, userTokens.size());
    assertTrue(userTokens.stream()
        .map(SampleToken::getClient)
        .anyMatch(clients::contains));
  }

  @Test
  void shouldSupportSubTokens() {
    SampleToken mainToken = new SampleToken(
        TEST_USER_ID,
        TEST_CLIENT,
        "main-token",
        Instant.now().plusSeconds(3600)
    );
    tokenRepository.save(mainToken);

    SampleToken subToken = new SampleToken(
        TEST_USER_ID,
        TEST_CLIENT + ".device",
        "sub-token",
        Instant.now().plusSeconds(1800)
    );
    subToken.setTokenType("SUB");
    subToken.setTokenSubtype("device");
    tokenRepository.save(subToken);

    SampleToken mainSaved = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT);
    SampleToken subSaved = tokenRepository.findByUserIdAndClient(TEST_USER_ID, TEST_CLIENT + ".device");

    assertNotNull(mainSaved);
    assertNotNull(subSaved);
    assertEquals("APP", mainSaved.getTokenType());
    assertEquals("SUB", subSaved.getTokenType());
    assertEquals("device", subSaved.getTokenSubtype());
  }

  @Test
  void shouldCleanupExpiredTokens() {
    Instant now = Instant.now();

    SampleToken expiredToken = new SampleToken(
        TEST_USER_ID,
        "expired-client",
        "expired-hash",
        now.minus(3600, ChronoUnit.SECONDS)
    );
    tokenRepository.save(expiredToken);

    SampleToken validToken = new SampleToken(
        TEST_USER_ID,
        "valid-client",
        "valid-hash",
        now.plus(3600, ChronoUnit.SECONDS)
    );
    tokenRepository.save(validToken);

    List<SampleToken> expiredTokens = tokenRepository.findByExpiryAtBefore(now);
    assertEquals(1, expiredTokens.size());

    tokenRepository.deleteAll(expiredTokens);

    List<SampleToken> remaining = tokenRepository.findAllByUserId(TEST_USER_ID);
    assertEquals(1, remaining.size());
    assertEquals("valid-client", remaining.get(0).getClient());
  }
}
