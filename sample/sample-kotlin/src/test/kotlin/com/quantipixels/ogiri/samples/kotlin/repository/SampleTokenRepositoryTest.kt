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

import com.quantipixels.ogiri.samples.kotlin.Application
import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class SampleTokenRepositoryTest {
  @Autowired private lateinit var tokenRepository: OgiriTokenRepository<SampleToken>
  @Autowired private lateinit var jpaRepository: SampleTokenJpaRepository

  private val testUserId = 1L
  private val testClient = "test-client"
  private val testToken = "hashed-token-123"

  @BeforeEach
  fun setUp() {
    jpaRepository.deleteAll()
  }

  @Test
  fun `should save and retrieve token by user and client`() {
    val token = createToken(testUserId, testClient, testToken)
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
    val token1 = createToken(testUserId, "client-1", "token-1")
    val token2 = createToken(testUserId, "client-2", "token-2")

    tokenRepository.save(token1)
    jpaRepository.flush()
    Thread.sleep(100) // Ensure different timestamps
    tokenRepository.save(token2)

    val tokens = tokenRepository.findAllByUserId(testUserId)

    assertEquals(2, tokens.size)
    val clients = tokens.map { it.client }
    assertTrue(clients.contains("client-1"))
    assertTrue(clients.contains("client-2"))
  }

  @Test
  fun `should find expired tokens`() {
    val now = Instant.now()

    val expiredToken =
        createToken(testUserId, "expired-client", testToken).apply {
          expiryAt = now.minus(1, ChronoUnit.HOURS)
        }
    val validToken =
        createToken(testUserId, "valid-client", testToken).apply {
          expiryAt = now.plus(1, ChronoUnit.HOURS)
        }

    tokenRepository.save(expiredToken)
    tokenRepository.save(validToken)

    val expired = tokenRepository.findByExpiryAtBefore(now)

    assertEquals(1, expired.size)
    assertEquals("expired-client", expired[0].client)
  }

  @Test
  fun `should delete token by user and client`() {
    val token = createToken(testUserId, testClient, testToken)
    tokenRepository.save(token)

    tokenRepository.deleteByUserIdAndClient(testUserId, testClient)

    val retrieved = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNull(retrieved)
  }

  @Test
  fun `should delete multiple tokens by client list`() {
    val token1 = createToken(testUserId, "client-1", "token-1")
    val token2 = createToken(testUserId, "client-2", "token-2")
    val token3 = createToken(testUserId, "client-3", "token-3")

    tokenRepository.save(token1)
    tokenRepository.save(token2)
    tokenRepository.save(token3)

    tokenRepository.deleteByUserIdAndClientIn(testUserId, listOf("client-1", "client-2"))

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertEquals(1, remaining.size)
    assertEquals("client-3", remaining[0].client)
  }

  @Test
  fun `should delete all tokens for user`() {
    val token1 = createToken(testUserId, "client-1", "token-1")
    val token2 = createToken(testUserId, "client-2", "token-2")

    tokenRepository.save(token1)
    tokenRepository.save(token2)

    tokenRepository.deleteByUserId(testUserId)

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertTrue(remaining.isEmpty())
  }

  @Test
  fun `should delete tokens from collection`() {
    val token1 = createToken(testUserId, "client-1", "token-1")
    val token2 = createToken(testUserId, "client-2", "token-2")

    val saved1 = tokenRepository.save(token1)
    tokenRepository.save(token2)

    tokenRepository.delete(saved1)

    val remaining = tokenRepository.findAllByUserId(testUserId)
    assertEquals(1, remaining.size)
    assertEquals("client-2", remaining[0].client)
  }

  @Test
  fun `should update token properties`() {
    var token = createToken(testUserId, testClient, testToken)
    token = tokenRepository.save(token)

    token.token = "new-hashed-token"
    token.lastUsedAt = Instant.now()
    tokenRepository.save(token)

    val updated = tokenRepository.findByUserIdAndClient(testUserId, testClient)
    assertNotNull(updated)
    assertEquals("new-hashed-token", updated!!.token)
    assertNotNull(updated.lastUsedAt)
  }

  private fun createToken(
      userId: Long,
      client: String,
      token: String,
  ): SampleToken =
      SampleToken().apply {
        this.userId = userId
        this.client = client
        this.token = token
        this.expiryAt = Instant.now().plus(1, ChronoUnit.HOURS)
      }
}
