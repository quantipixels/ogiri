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
package com.quantipixels.ogiri.security.web

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.DefaultIdentifierPolicy
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.helpers.AuthenticationBypassDecider
import com.quantipixels.ogiri.security.routes.OgiriRoute
import com.quantipixels.ogiri.security.routes.OgiriRouteCatalog
import com.quantipixels.ogiri.security.routes.OgiriRouteRegistry
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import com.quantipixels.ogiri.security.tokens.DefaultOgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint

class OgiriTokenAuthenticationFilterTest {
  @Suppress("DEPRECATION")
  private val passwordEncoder: PasswordEncoder =
      object : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String = rawPassword.toString()

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean =
            rawPassword.toString() == encodedPassword
      }
  private val identifierPolicy = DefaultIdentifierPolicy()

  private val user = TestFixtures.testUser(userId = 1L, username = "user")
  private val userDirectory =
      object : OgiriUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.getOgiriUserId() == id }

        override fun findByEmail(email: String) = null

        override fun findByUsername(username: String) = user.takeIf { it.username == username }

        override fun recordSuccessfulLogin(userId: Long) {}
      }

  private fun defaultAuthProperties() =
      OgiriConfigurationProperties().apply {
        auth.apply {
          maxClients = 24
          batchGraceSeconds = 5
          tokenLifespanDays = 14
          rotateStaleSeconds = 0 // Disable staleness-based rotation for tests
        }
      }

  // Custom TokenService that implements tokenFactory for TestToken
  private inner class TestTokenService(
      repository: OgiriTokenRepository<TestToken>,
      passwordEncoder: PasswordEncoder,
      userDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
  ) :
      OgiriTokenService<TestToken>(
          repository,
          passwordEncoder,
          userDirectory,
          identifierPolicy,
          subTokenRegistry,
          properties) {
    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: com.quantipixels.ogiri.security.tokens.OgiriTokenType,
        expiry: Instant,
        tokenSubtype: String?,
        plainTokenValue: String,
    ): TestToken =
        TestToken(
                userId = userId,
                client = client,
                token = hashedToken,
                tokenType = tokenType.name,
                expiryAt = expiry,
                tokenSubtype = tokenSubtype,
            )
            .apply { plainToken = plainTokenValue }
  }

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  @Test
  fun `filter bypasses when decider allows`() {
    val bypassRoutes =
        OgiriRouteCatalog(
            listOf(
                object : OgiriRouteRegistry {
                  override fun routes() = listOf(OgiriRoute.get("/public", useAuth = false))
                },
            ),
        )
    val bypassDecider = AuthenticationBypassDecider(bypassRoutes)
    val entryPoint = RecordingEntryPoint()
    val filter = newFilter(InMemoryTokenRepository(), bypassDecider, entryPoint).filter

    val request = MockHttpServletRequest("GET", "/public")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    filter.doFilter(request, response, chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    // Entry point should never be called on bypass
    assertNull(entryPoint.lastRequest)
  }

  @Test
  fun `filter authenticates within batch window without rotation`() {
    val fixture = newFilter()

    // Issue a token using the same TokenService used by the filter
    val headers: AuthHeader =
        fixture.tokenService.createNewAuthToken(user.getOgiriUserId(), "clientA")

    val request = MockHttpServletRequest("GET", "/api/secure")
    request.addHeader("access-token", headers.accessToken!!)
    request.addHeader("client", headers.client!!)
    request.addHeader("uid", headers.uid!!)
    request.addHeader("expiry", headers.expiry!!)
    request.addHeader("access-token-kind", "app")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    fixture.filter.doFilter(request, response, chain)

    val issuedToken =
        fixture.repository.findByUserIdAndClient(user.getOgiriUserId(), "clientA").orElse(null)

    // Authentication should be present
    assertNotNull(SecurityContextHolder.getContext().authentication)
    // Batch window requests should record activity on the token
    assertNotNull(issuedToken?.lastUsedAt)
    // Entry point should not be called
    assertNull(fixture.entryPoint.lastRequest)
    // Freshly issued tokens are treated as batch requests; no rotation headers are appended
    assertNull(response.getHeader("access-token"))
  }

  private data class FilterFixture(
      val repository: OgiriTokenRepository<TestToken>,
      val tokenService: OgiriTokenService<TestToken>,
      val entryPoint: RecordingEntryPoint,
      val filter: OgiriTokenAuthenticationFilter,
  )

  private fun newFilter(
      repository: OgiriTokenRepository<TestToken> = InMemoryTokenRepository(),
      bypassDecider: AuthenticationBypassDecider =
          AuthenticationBypassDecider(OgiriRouteCatalog(emptyList())),
      entryPoint: RecordingEntryPoint = RecordingEntryPoint(),
  ): FilterFixture {
    val properties = OgiriConfigurationProperties()
    val tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            DefaultOgiriSubTokenRegistry(emptyList()),
            defaultAuthProperties(),
        )
    val filter =
        OgiriTokenAuthenticationFilter(
            userDirectory,
            tokenService,
            entryPoint,
            bypassDecider,
            identifierPolicy,
            properties,
        )
    return FilterFixture(repository, tokenService, entryPoint, filter)
  }
}

private class RecordingEntryPoint : AuthenticationEntryPoint {
  var lastRequest: HttpServletRequest? = null

  override fun commence(
      request: HttpServletRequest,
      response: HttpServletResponse,
      authException: org.springframework.security.core.AuthenticationException,
  ) {
    lastRequest = request
  }
}
