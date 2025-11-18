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

import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.core.appendAuthHeaders
import com.quantipixels.ogiri.security.core.extractAuthHeader
import com.quantipixels.ogiri.security.helpers.AuthenticationBypassDecider
import com.quantipixels.ogiri.security.spi.TokenUser
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import com.quantipixels.ogiri.security.tokens.TokenService
import com.quantipixels.ogiri.security.tokens.TokenType
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException
import java.time.Instant

/**
 * Once-per-request token authentication filter.
 *
 * - Skips auth for whitelisted/public/preflight requests via [AuthenticationBypassDecider]
 * - Validates APP tokens and rotates on configurable policies
 * - Emits refreshed headers (including sub-tokens) when rotation occurs
 * - Populates the SecurityContext with the authenticated [TokenUser]
 */
open class OgiriTokenAuthenticationFilter(
  private val userDirectory: TokenUserDirectory,
  private val tokenService: TokenService<*>,
  private val authenticationEntryPoint: AuthenticationEntryPoint,
  private val bypassDecider: AuthenticationBypassDecider,
  private val identifierPolicy: IdentifierPolicy,
) : OncePerRequestFilter() {
  @Value(value = "\${ogiri.auth.rotate-on-write-only:false}")
  private var rotateOnWriteOnly: Boolean = false

  @Value(value = "\${ogiri.auth.rotate-stale-seconds:0}")
  private var rotateStaleSeconds: Long = 0

  @Throws(ServletException::class, IOException::class)
  override fun doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain,
  ) {
    if (bypassDecider.canSkip(request)) {
      filterChain.doFilter(request, response)
      return
    }

    try {
      val authResult = authenticateRequest(request)
      afterAuth(request, response, authResult)
      if (authResult != null) {
        authResult.authHeader?.let { response.appendAuthHeaders(it) }
        SecurityContextHolder.getContext().authentication =
          buildAuthentication(authResult.user, request)
      }
    } catch (e: AuthenticationException) {
      SecurityContextHolder.clearContext()
      authenticationEntryPoint.commence(request, response, e)
      return
    }

    filterChain.doFilter(request, response)
  }

  protected open fun beforeAuth(request: HttpServletRequest) {}

  protected open fun afterAuth(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authResult: AuthResult?,
  ) {}

  protected data class AuthResult(val user: TokenUser, val authHeader: AuthHeader?)

  @Throws(AuthenticationException::class)
  protected open fun authenticateRequest(request: HttpServletRequest): AuthResult? {
    beforeAuth(request)
    val headerToken = request.extractAuthHeader()
    if (!headerToken.isValid()) return null

    val requestStartedAt = Instant.now()
    val client = headerToken.client?.also { validateIdentifier(it, "error.auth.bad_client_id") }!!
    val uid = headerToken.uid?.also { validateIdentifier(it, "error.auth.bad_uid") }!!
    ensureAppToken(headerToken.kind)

    val user =
      userDirectory.loadUserByUsername(uid) as? TokenUser
        ?: throw BadCredentialsException("error.auth.bad_credentials")
    val token = headerToken.accessToken!!
    if (!tokenService.validToken(token, user, client)) {
      throw BadCredentialsException("error.auth.bad_credentials")
    }

    val authHeader =
      if (tokenService.isBatchRequest(user, client, requestStartedAt)) {
        tokenService.extendBatchBuffer(user, token, client)
        null
      } else {
        rotateTokensIfNeeded(user, client, request.method)
      }

    return AuthResult(user, authHeader)
  }

  private fun rotateTokensIfNeeded(
    user: TokenUser,
    client: String,
    method: String,
  ): AuthHeader? {
    if (rotateOnWriteOnly && isSafeMethod(method)) return null
    val shouldRotate =
      if (rotateStaleSeconds > 0) {
        tokenService.shouldRotate(user, client, rotateStaleSeconds)
      } else {
        true
      }
    return if (shouldRotate) tokenService.createNewAuthToken(user.userId, client) else null
  }

  private fun isSafeMethod(method: String): Boolean =
    method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

  private fun ensureAppToken(kind: String?) {
    val tokenKind = runCatching { TokenType.valueOf(kind?.trim()?.uppercase() ?: "APP") }.getOrDefault(TokenType.APP)
    if (tokenKind != TokenType.APP) throw BadCredentialsException("error.auth.bad_token_type")
  }

  private fun validateIdentifier(
    value: String,
    errorCode: String,
  ) {
    if (!identifierPolicy.isValid(value)) throw BadCredentialsException(errorCode)
  }

  private fun buildAuthentication(
    user: TokenUser,
    request: HttpServletRequest,
  ) = UsernamePasswordAuthenticationToken(user, "[PROTECTED_PASSWORD]", user.authorities).apply {
    details = WebAuthenticationDetailsSource().buildDetails(request)
  }
}
