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
import com.quantipixels.ogiri.samples.kotlin.service.SampleTokenService
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenRotationTest {

  @Autowired private lateinit var tokenService: SampleTokenService
  @Autowired private lateinit var tokenRepository: SampleTokenRepository

  private val testUserId = 1L
  private val testUsername = "user1" // Username for user ID 1
  private val testClient = "rotation-test-client"

  @BeforeEach
  fun setUp() {
    tokenRepository.deleteAll()
  }

  @Test
  fun `should create new auth token`() {
    val authHeader = tokenService.createNewAuthToken(testUserId, testClient, null)

    assertNotNull(authHeader)
    assertNotNull(authHeader.accessToken)
    assertEquals(testClient, authHeader.client)
    assertEquals(testUsername, authHeader.uid) // UID is username, not numeric ID
    assertNotNull(authHeader.expiry)

    // Verify token persisted
    val savedToken = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)
    assertNotNull(savedToken)
    assertEquals(testUserId, savedToken!!.userId)
    assertEquals(testClient, savedToken.client)
  }

  @Test
  fun `should rotate token on subsequent creation`() {
    // Create first token
    val firstAuth = tokenService.createNewAuthToken(testUserId, testClient, null)
    val firstToken = firstAuth.accessToken

    // Simulate time passing beyond grace period
    val token = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)
    token!!.lastUsedAt = Instant.now().minus(10, ChronoUnit.SECONDS)
    tokenRepository.save(token)
    tokenRepository.flush()

    // Create second token (rotation)
    val secondAuth = tokenService.createNewAuthToken(testUserId, testClient, null)
    val secondToken = secondAuth.accessToken

    // Tokens should be different
    assertNotEquals(firstToken, secondToken)
  }

  @Test
  fun `should preserve last token for grace period`() {
    // Create initial token
    tokenService.createNewAuthToken(testUserId, testClient, null)

    val token = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)
    val originalHash = token!!.token

    // Simulate update that preserves last token
    token.lastToken = originalHash
    token.token = "new-hash-value"
    tokenRepository.save(token)
    tokenRepository.flush()

    // Verify last token preserved
    val updated = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)
    assertEquals("new-hash-value", updated!!.token)
    assertEquals(originalHash, updated.lastToken)
  }

  @Test
  fun `should support three tier grace period`() {
    // Create token with all three tiers
    tokenService.createNewAuthToken(testUserId, testClient, null)

    val token = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)

    // Set up three-tier history
    token!!.lastToken = "previous-hash"
    token.previousToken = "oldest-hash"
    tokenRepository.save(token)
    tokenRepository.flush()

    val updated = tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null)
    assertNotNull(updated!!.token)
    assertEquals("previous-hash", updated.lastToken)
    assertEquals("oldest-hash", updated.previousToken)
  }

  @Test
  fun `should delete token successfully`() {
    tokenService.createNewAuthToken(testUserId, testClient, null)

    // Verify token exists
    assertNotNull(tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null))

    // Delete
    tokenService.deleteToken(testUserId, testClient)

    // Verify deleted
    assertNull(tokenRepository.findByUserIdAndClient(testUserId, testClient).orElse(null))
  }

  @Test
  fun `should delete all tokens for user`() {
    // Create multiple tokens for same user
    tokenService.createNewAuthToken(testUserId, "client-1", null)
    tokenService.createNewAuthToken(testUserId, "client-2", null)
    tokenService.createNewAuthToken(testUserId, "client-3", null)

    assertEquals(3, tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).size)

    // Delete all for user
    tokenService.deleteAllForUser(testUserId)

    assertEquals(0, tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).size)
  }

  @Test
  fun `should get all tokens by user id`() {
    tokenService.createNewAuthToken(testUserId, "client-a", null)
    tokenService.createNewAuthToken(testUserId, "client-b", null)

    val tokens = tokenService.getAllByUserId(testUserId)

    assertEquals(2, tokens.size)
  }

  @Test
  fun `should cleanup expired tokens`() {
    // Create expired token directly
    val expiredToken =
        SampleToken().apply {
          userId = testUserId
          client = "expired-client"
          token = "hash"
          expiryAt = Instant.now().minus(1, ChronoUnit.HOURS)
        }
    tokenRepository.save(expiredToken)

    // Create valid token
    tokenService.createNewAuthToken(testUserId, "valid-client", null)

    // Run cleanup
    val deleted = tokenService.cleanupExpiredTokens(Instant.now())

    assertEquals(1, deleted)
    assertNull(tokenRepository.findByUserIdAndClient(testUserId, "expired-client").orElse(null))
    assertNotNull(tokenRepository.findByUserIdAndClient(testUserId, "valid-client").orElse(null))
  }
}
