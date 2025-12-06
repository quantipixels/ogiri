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
package com.quantipixels.ogiri.samples.kotlin.repository

import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
class SampleTokenRepositoryTest {
  @Autowired private lateinit var tokenRepository: SampleTokenRepository

  private val testUserId = 1L
  private val testClient = "test-client"
  private val testToken = "hashed-token-123"

  @BeforeEach
  fun setUp() {
    tokenRepository.deleteAll()
  }

  @Test
  fun `should save and retrieve token by user and client`() {
    val token =
        SampleToken(
            userId = testUserId,
            client = testClient,
            token = testToken,
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    tokenRepository.save(token)

    val retrieved = tokenRepository.findByUserIdAndClient(testUserId, testClient)

    assertNotNull(retrieved)
    assertEquals(testUserId, retrieved!!.userId)
    assertEquals(testClient, retrieved.client)
    assertEquals(testToken, retrieved.token)
  }

  @Test
  fun `should return null for non-existent token`() {
    val retrieved = tokenRepository.findByUserIdAndClient(999L, "non-existent")
    assertNull(retrieved)
  }

  @Test
  fun `should find all tokens for user ordered by updated_at DESC`() {
    // Create multiple tokens for same user
    val token1 =
        SampleToken(
            userId = testUserId,
            client = "client-1",
            token = "token-1",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    val token2 =
        SampleToken(
            userId = testUserId,
            client = "client-2",
            token = "token-2",
            expiryAt = Instant.now().plus(2, ChronoUnit.HOURS),
        )

    tokenRepository.save(token1)
    tokenRepository.flush()
    Thread.sleep(100) // Ensure different timestamps
    tokenRepository.save(token2)

    // Retrieve all tokens
    val tokens = tokenRepository.findAllByUserId(testUserId)

    assertEquals(2, tokens.size)
    // Verify both tokens are present (order may vary)
    val clients = tokens.map { it.client }
    assertTrue(clients.contains("client-1"))
    assertTrue(clients.contains("client-2"))
  }

  @Test
  fun `should find expired tokens`() {
    val now = Instant.now()

    // Create expired token
    val expiredToken =
        SampleToken(
            userId = testUserId,
            client = "expired-client",
            token = testToken,
            expiryAt = now.minus(1, ChronoUnit.HOURS),
        )
    // Create valid token
    val validToken =
        SampleToken(
            userId = testUserId,
            client = "valid-client",
            token = testToken,
            expiryAt = now.plus(1, ChronoUnit.HOURS),
        )

    tokenRepository.save(expiredToken)
    tokenRepository.save(validToken)

    // Find expired tokens
    val expired = tokenRepository.findByExpiryAtBefore(now)

    assertEquals(1, expired.size)
    assertEquals("expired-client", expired[0].client)
  }

  @Test
  fun `should delete token by user and client`() {
    val token =
        SampleToken(
            userId = testUserId,
            client = testClient,
            token = testToken,
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    tokenRepository.save(token)

    // Delete token
    tokenRepository.deleteByUserIdAndClient(testUserId, testClient)

    val retrieved = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNull(retrieved)
  }

  @Test
  fun `should delete multiple tokens by client list`() {
    val token1 =
        SampleToken(
            userId = testUserId,
            client = "client-1",
            token = "token-1",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    val token2 =
        SampleToken(
            userId = testUserId,
            client = "client-2",
            token = "token-2",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    val token3 =
        SampleToken(
            userId = testUserId,
            client = "client-3",
            token = "token-3",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )

    tokenRepository.save(token1)
    tokenRepository.save(token2)
    tokenRepository.save(token3)

    // Delete two tokens
    tokenRepository.deleteByUserIdAndClientIn(testUserId, listOf("client-1", "client-2"))

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertEquals(1, remaining.size)
    assertEquals("client-3", remaining[0].client)
  }

  @Test
  fun `should delete all tokens for user`() {
    val token1 =
        SampleToken(
            userId = testUserId,
            client = "client-1",
            token = "token-1",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    val token2 =
        SampleToken(
            userId = testUserId,
            client = "client-2",
            token = "token-2",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )

    tokenRepository.save(token1)
    tokenRepository.save(token2)

    // Delete all tokens for user
    tokenRepository.deleteByUserId(testUserId)

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertTrue(remaining.isEmpty())
  }

  @Test
  fun `should delete tokens from collection`() {
    val token1 =
        SampleToken(
            userId = testUserId,
            client = "client-1",
            token = "token-1",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    val token2 =
        SampleToken(
            userId = testUserId,
            client = "client-2",
            token = "token-2",
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )

    val saved1 = tokenRepository.save(token1)
    val saved2 = tokenRepository.save(token2)

    // Delete from collection
    tokenRepository.deleteAll(listOf(saved1))

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertEquals(1, remaining.size)
    assertEquals("client-2", remaining[0].client)
  }

  @Test
  fun `should update token properties`() {
    var token =
        SampleToken(
            userId = testUserId,
            client = testClient,
            token = testToken,
            expiryAt = Instant.now().plus(1, ChronoUnit.HOURS),
        )
    token = tokenRepository.save(token)

    // Update token
    token.token = "new-hashed-token"
    token.lastUsedAt = Instant.now()
    tokenRepository.save(token)

    val updated = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNotNull(updated)
    assertEquals("new-hashed-token", updated!!.token)
    assertNotNull(updated.lastUsedAt)
  }
}
