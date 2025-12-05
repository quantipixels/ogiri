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

import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import com.quantipixels.ogiri.samples.kotlin.repository.SampleTokenRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenServiceIntegrationTest {

  @Autowired
  private lateinit var tokenRepository: SampleTokenRepository

  private val testUserId = 1L
  private val testClient = "test-app"

  @BeforeEach
  fun setUp() {
    tokenRepository.deleteAll()
  }

  @Test
  fun `should create and save new token`() {
    val token = SampleToken(
      userId = testUserId,
      client = testClient,
      token = "hashed-token-value",
      expiryAt = Instant.now().plusSeconds(3600)
    )
    token.plainToken = "plain-token-value"
    tokenRepository.save(token)

    val savedToken = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNotNull(savedToken)
    assertEquals(testUserId, savedToken!!.userId)
    assertEquals(testClient, savedToken.client)
    assertEquals("hashed-token-value", savedToken.token)
    assertEquals("APP", savedToken.tokenType)
  }

  @Test
  fun `should support token rotation with grace period`() {
    // Save initial token
    val token1 = SampleToken(
      userId = testUserId,
      client = testClient,
      token = "token-hash-1",
      expiryAt = Instant.now().plusSeconds(3600)
    )
    tokenRepository.save(token1)

    // Rotate token by deleting old and saving new
    tokenRepository.deleteByUserIdAndClient(testUserId, testClient)
    tokenRepository.flush()  // Ensure delete is flushed before saving new token

    val token2 = SampleToken(
      userId = testUserId,
      client = testClient,
      token = "token-hash-2",
      expiryAt = Instant.now().plusSeconds(3600)
    )
    tokenRepository.save(token2)

    val rotatedToken = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNotNull(rotatedToken)
    assertEquals("token-hash-2", rotatedToken!!.token)
  }

  @Test
  fun `should handle multiple concurrent clients for same user`() {
    val clients = listOf("mobile", "web", "desktop")

    for (client in clients) {
      val token = SampleToken(
        userId = testUserId,
        client = client,
        token = "hash-$client",
        expiryAt = Instant.now().plusSeconds(3600)
      )
      tokenRepository.save(token)
    }

    val userTokens = tokenRepository.findAllByUserId(testUserId)
    assertEquals(3, userTokens.size)
    assertTrue(userTokens.map { it.client }.containsAll(clients))
  }

  @Test
  fun `should support sub-tokens`() {
    val mainToken = SampleToken(
      userId = testUserId,
      client = testClient,
      token = "main-token",
      expiryAt = Instant.now().plusSeconds(3600)
    )
    tokenRepository.save(mainToken)

    val subToken = SampleToken(
      userId = testUserId,
      client = "$testClient.device",
      token = "sub-token",
      expiryAt = Instant.now().plusSeconds(1800),
      tokenSubtype = "device"
    )
    tokenRepository.save(subToken)

    val mainSaved = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    val subSaved = tokenRepository.findByUserIdAndClient(testUserId, "$testClient.device")

    assertNotNull(mainSaved)
    assertNotNull(subSaved)
    assertEquals("APP", mainSaved!!.tokenType)
    assertEquals("APP", subSaved!!.tokenType)
    assertEquals("device", subSaved.tokenSubtype)
  }

  @Test
  fun `should cleanup expired tokens`() {
    val now = Instant.now()

    val expiredToken = SampleToken(
      userId = testUserId,
      client = "expired-client",
      token = "expired-hash",
      expiryAt = now.minus(1, ChronoUnit.HOURS)
    )
    tokenRepository.save(expiredToken)

    val validToken = SampleToken(
      userId = testUserId,
      client = "valid-client",
      token = "valid-hash",
      expiryAt = now.plus(1, ChronoUnit.HOURS)
    )
    tokenRepository.save(validToken)

    val expiredTokens = tokenRepository.findByExpiryAtBefore(now)
    assertEquals(1, expiredTokens.size)

    tokenRepository.deleteAll(expiredTokens)

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertEquals(1, remaining.size)
    assertEquals("valid-client", remaining[0].client)
  }
}
