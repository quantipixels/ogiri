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

import com.quantipixels.ogiri.samples.kotlin.repository.SampleTokenRepository
import com.quantipixels.ogiri.samples.kotlin.service.SampleTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MultiClientSessionTest {

  @Autowired private lateinit var tokenService: SampleTokenService
  @Autowired private lateinit var tokenRepository: SampleTokenRepository

  private val testUserId = 1L

  @BeforeEach
  fun setUp() {
    tokenRepository.deleteAll()
  }

  @Test
  fun `should support multiple clients per user`() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(testUserId, "mobile", null)
    tokenService.createNewAuthToken(testUserId, "web", null)
    tokenService.createNewAuthToken(testUserId, "desktop", null)

    val tokens = tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId)

    assertEquals(3, tokens.size)

    val clients = tokens.map { it.client }.toSet()
    assertTrue(clients.contains("mobile"))
    assertTrue(clients.contains("web"))
    assertTrue(clients.contains("desktop"))
  }

  @Test
  fun `should allow logout from single client`() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(testUserId, "mobile", null)
    tokenService.createNewAuthToken(testUserId, "web", null)
    tokenService.createNewAuthToken(testUserId, "desktop", null)

    // Logout from mobile only
    tokenService.deleteToken(testUserId, "mobile")

    val remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId)
    assertEquals(2, remaining.size)

    val clients = remaining.map { it.client }.toSet()
    assertFalse(clients.contains("mobile"))
    assertTrue(clients.contains("web"))
    assertTrue(clients.contains("desktop"))
  }

  @Test
  fun `should allow logout from all clients`() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(testUserId, "mobile", null)
    tokenService.createNewAuthToken(testUserId, "web", null)
    tokenService.createNewAuthToken(testUserId, "desktop", null)

    assertEquals(3, tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).size)

    // Logout from all clients
    tokenService.deleteAllForUser(testUserId)

    assertEquals(0, tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).size)
  }

  @Test
  fun `should allow bulk client logout`() {
    // Create tokens for multiple clients
    tokenService.createNewAuthToken(testUserId, "mobile", null)
    tokenService.createNewAuthToken(testUserId, "web", null)
    tokenService.createNewAuthToken(testUserId, "desktop", null)
    tokenService.createNewAuthToken(testUserId, "tablet", null)

    // Bulk logout from mobile and web
    tokenService.deleteToken(testUserId, listOf("mobile", "web"))

    val remaining = tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId)
    assertEquals(2, remaining.size)

    val clients = remaining.map { it.client }.toSet()
    assertTrue(clients.contains("desktop"))
    assertTrue(clients.contains("tablet"))
  }

  @Test
  fun `should isolate tokens between users`() {
    val user1 = 1L
    val user2 = 2L

    tokenService.createNewAuthToken(user1, "mobile", null)
    tokenService.createNewAuthToken(user1, "web", null)
    tokenService.createNewAuthToken(user2, "mobile", null)

    assertEquals(2, tokenRepository.findByUserIdOrderByUpdatedAtDesc(user1).size)
    assertEquals(1, tokenRepository.findByUserIdOrderByUpdatedAtDesc(user2).size)

    // Deleting user1's tokens doesn't affect user2
    tokenService.deleteAllForUser(user1)

    assertEquals(0, tokenRepository.findByUserIdOrderByUpdatedAtDesc(user1).size)
    assertEquals(1, tokenRepository.findByUserIdOrderByUpdatedAtDesc(user2).size)
  }

  @Test
  fun `should update existing client token`() {
    // Create initial token
    tokenService.createNewAuthToken(testUserId, "mobile", null)

    val firstToken = tokenRepository.findByUserIdAndClient(testUserId, "mobile").orElse(null)
    val firstId = firstToken!!.id

    // Create token for same client again (should update)
    tokenService.createNewAuthToken(testUserId, "mobile", null)

    // Should still have only one token for this client
    val tokens =
        tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).filter {
          it.client == "mobile"
        }

    assertEquals(1, tokens.size)
  }

  @Test
  fun `should get token by user id and client`() {
    tokenService.createNewAuthToken(testUserId, "specific-client", null)

    val token = tokenService.getByUserIdAndClient(testUserId, "specific-client")

    assertNotNull(token)
    assertEquals("specific-client", token!!.client)
    assertEquals(testUserId, token.userId)
  }

  @Test
  fun `should return null for non existent client`() {
    val token = tokenService.getByUserIdAndClient(testUserId, "nonexistent")

    assertNull(token)
  }
}
