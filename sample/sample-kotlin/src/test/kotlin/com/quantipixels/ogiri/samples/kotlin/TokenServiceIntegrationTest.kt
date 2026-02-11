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
import com.quantipixels.ogiri.security.core.ACCESS_TOKEN
import com.quantipixels.ogiri.security.core.CLIENT
import com.quantipixels.ogiri.security.core.EXPIRY
import com.quantipixels.ogiri.security.core.SecurityServiceException
import com.quantipixels.ogiri.security.core.UID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TokenServiceIntegrationTest {
  @Autowired private lateinit var tokenService: SampleTokenService
  @Autowired private lateinit var tokenRepository: SampleTokenRepository

  private val testUserId = 1L
  private val testEmail = "user1@example.com"
  private val testPassword = "password"

  @BeforeEach
  fun setUp() {
    tokenRepository.deleteAll()
    SecurityContextHolder.clearContext()
  }

  @Test
  fun `createNewAuthToken with null client generates client and persists APP token`() {
    val authHeader = tokenService.createNewAuthToken(testUserId, null, null)

    assertNotNull(authHeader.accessToken)
    assertNotNull(authHeader.client)
    assertEquals("user1", authHeader.uid)

    val savedToken =
        tokenRepository.findByUserIdAndClient(testUserId, authHeader.client!!).orElse(null)
    assertNotNull(savedToken)
    assertEquals(testUserId, savedToken!!.userId)
    assertEquals(authHeader.client, savedToken.client)
    assertEquals("APP", savedToken.tokenType)
  }

  @Test
  fun `verifyUser authenticates and appends auth headers`() {
    val request =
        MockHttpServletRequest("POST", "/api/auth/login").apply { remoteAddr = "127.0.0.1" }
    val response = MockHttpServletResponse()

    tokenService.verifyUser(request, response, testEmail, testPassword)

    val authentication = SecurityContextHolder.getContext().authentication
    assertNotNull(authentication)
    assertEquals("user1", authentication.name)
    assertNotNull(response.getHeader(ACCESS_TOKEN))
    assertNotNull(response.getHeader(CLIENT))
    assertEquals("user1", response.getHeader(UID))
    assertNotNull(response.getHeader(EXPIRY))
  }

  @Test
  fun `verifyUser rejects invalid credentials without creating auth context`() {
    val request =
        MockHttpServletRequest("POST", "/api/auth/login").apply { remoteAddr = "127.0.0.1" }
    val response = MockHttpServletResponse()

    assertThrows(SecurityServiceException::class.java) {
      tokenService.verifyUser(request, response, testEmail, "wrong-password")
    }
    assertNull(SecurityContextHolder.getContext().authentication)
    assertEquals(0, tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).size)
  }

  @Test
  fun `createNewAuthToken rotates token for same client while keeping single persisted row`() {
    val first = tokenService.createNewAuthToken(testUserId, "web", null)
    val second = tokenService.createNewAuthToken(testUserId, "web", null)

    assertNotEquals(first.accessToken, second.accessToken)
    val webTokens =
        tokenRepository.findByUserIdOrderByUpdatedAtDesc(testUserId).filter { it.client == "web" }
    assertEquals(1, webTokens.size)
  }

  @Test
  fun `deleteToken removes only targeted client token for same user`() {
    tokenService.createNewAuthToken(testUserId, "mobile", null)
    tokenService.createNewAuthToken(testUserId, "web", null)

    tokenService.deleteToken(testUserId, "mobile")

    assertNull(tokenRepository.findByUserIdAndClient(testUserId, "mobile").orElse(null))
    assertNotNull(tokenRepository.findByUserIdAndClient(testUserId, "web").orElse(null))
  }

  @Test
  fun `revokeClient removes token for client represented by headers`() {
    val issued = tokenService.createNewAuthToken(testUserId, "mobile", null)
    assertNotNull(tokenRepository.findByUserIdAndClient(testUserId, "mobile").orElse(null))
    val accessToken = requireNotNull(issued.accessToken)
    val client = requireNotNull(issued.client)
    val uid = requireNotNull(issued.uid)
    val expiry = requireNotNull(issued.expiry)

    val request =
        MockHttpServletRequest("POST", "/api/auth/logout").apply {
          addHeader(ACCESS_TOKEN, accessToken)
          addHeader(CLIENT, client)
          addHeader(UID, uid)
          addHeader(EXPIRY, expiry)
        }
    val response = MockHttpServletResponse()

    tokenService.revokeClient(testUserId, request, response)

    assertNull(tokenRepository.findByUserIdAndClient(testUserId, "mobile").orElse(null))
    assertEquals(accessToken, response.getHeader(ACCESS_TOKEN))
    assertEquals(client, response.getHeader(CLIENT))
  }
}
