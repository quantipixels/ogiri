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
import com.quantipixels.ogiri.security.routes.Route
import com.quantipixels.ogiri.security.routes.RouteCatalog
import com.quantipixels.ogiri.security.routes.RouteRegistry
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestFixtures
import com.quantipixels.ogiri.security.testutil.TestToken
import com.quantipixels.ogiri.security.tokens.DefaultSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.TokenRepository
import com.quantipixels.ogiri.security.tokens.TokenService
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
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint

class OgiriTokenAuthenticationFilterTest {
  private val passwordEncoder: PasswordEncoder = NoOpPasswordEncoder.getInstance()
  private val identifierPolicy = DefaultIdentifierPolicy()

  private val user = TestFixtures.testUser(userId = 1L, username = "user")
  private val userDirectory =
      object : TokenUserDirectory {
        override fun loadUserByUsername(username: String) = user

        override fun findById(id: Long) = user.takeIf { it.userId == id }

        override fun findByEmail(email: String) = null

        override fun findByUsername(username: String) = user.takeIf { it.username == username }

        override fun recordSuccessfulLogin(userId: Long) {}
      }

  // Custom TokenService that implements tokenFactory for TestToken
  private inner class TestTokenService(
      repository: TokenRepository<TestToken>,
      passwordEncoder: PasswordEncoder,
      userDirectory: TokenUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: com.quantipixels.ogiri.security.tokens.SubTokenRegistry,
      maxClients: Long = 24,
      batchGraceSeconds: Long = 5,
      tokenLifespanDays: Long = 14,
  ) :
      TokenService<TestToken>(
          repository,
          passwordEncoder,
          userDirectory,
          identifierPolicy,
          subTokenRegistry,
          maxClients,
          batchGraceSeconds,
          tokenLifespanDays,
      ) {
    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: com.quantipixels.ogiri.security.tokens.TokenType,
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
        RouteCatalog(
            listOf(
                object : RouteRegistry {
                  override fun routes() = listOf(Route.get("/public", useAuth = false))
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
    val headers: AuthHeader = fixture.tokenService.createNewAuthToken(user.userId, "clientA")
    val issuedToken = fixture.repository.findByUserIdAndClient(user.userId, "clientA")!!

    val request = MockHttpServletRequest("GET", "/api/secure")
    request.addHeader("access-token", headers.accessToken!!)
    request.addHeader("client", headers.client!!)
    request.addHeader("uid", headers.uid!!)
    request.addHeader("expiry", headers.expiry!!)
    request.addHeader("access-token-kind", "APP")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    fixture.filter.doFilter(request, response, chain)

    // Authentication should be present
    assertNotNull(SecurityContextHolder.getContext().authentication)
    // Batch window requests should record activity on the token
    assertNotNull(issuedToken.lastUsedAt)
    // Entry point should not be called
    assertNull(fixture.entryPoint.lastRequest)
    // Freshly issued tokens are treated as batch requests; no rotation headers are appended
    assertNull(response.getHeader("access-token"))
  }

  @Test
  fun `filter rotates tokens outside batch window`() {
    val fixture = newFilter()

    val headers: AuthHeader = fixture.tokenService.createNewAuthToken(user.userId, "clientA")
    val stored = fixture.repository.findByUserIdAndClient(user.userId, "clientA")!!
    // Simulate a stale request outside the batch grace window
    stored.updatedAt = Instant.now().minusSeconds(10)

    val request = MockHttpServletRequest("POST", "/api/secure")
    request.addHeader("access-token", headers.accessToken!!)
    request.addHeader("client", headers.client!!)
    request.addHeader("uid", headers.uid!!)
    request.addHeader("expiry", headers.expiry!!)
    request.addHeader("access-token-kind", "APP")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    fixture.filter.doFilter(request, response, chain)

    assertNotNull(SecurityContextHolder.getContext().authentication)
    assertNull(fixture.entryPoint.lastRequest)
    // Outside the batch window, the filter should rotate and emit new headers
    assertNotNull(response.getHeader("access-token"))
  }

  private data class FilterFixture(
      val repository: TokenRepository<TestToken>,
      val tokenService: TokenService<TestToken>,
      val entryPoint: RecordingEntryPoint,
      val filter: OgiriTokenAuthenticationFilter,
  )

  private fun newFilter(
      repository: TokenRepository<TestToken> = InMemoryTokenRepository(),
      bypassDecider: AuthenticationBypassDecider =
          AuthenticationBypassDecider(RouteCatalog(emptyList())),
      entryPoint: RecordingEntryPoint = RecordingEntryPoint(),
  ): FilterFixture {
    val properties = OgiriConfigurationProperties()
    val tokenService =
        TestTokenService(
            repository,
            passwordEncoder,
            userDirectory,
            identifierPolicy,
            DefaultSubTokenRegistry(emptyList()),
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
