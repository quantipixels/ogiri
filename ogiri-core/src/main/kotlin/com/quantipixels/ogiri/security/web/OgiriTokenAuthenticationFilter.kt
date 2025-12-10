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
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.core.appendAuthHeaders
import com.quantipixels.ogiri.security.core.extractAuthHeader
import com.quantipixels.ogiri.security.core.parseBearerToken
import com.quantipixels.ogiri.security.helpers.AuthenticationBypassDecider
import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.TokenService
import com.quantipixels.ogiri.security.tokens.TokenType
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import java.time.Instant
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Once-per-request token authentication filter.
 * - Skips auth for whitelisted/public/preflight requests via [AuthenticationBypassDecider]
 * - Validates APP tokens and rotates on configurable policies from [OgiriConfigurationProperties]
 * - Emits refreshed headers (including sub-tokens) when rotation occurs
 * - Populates the SecurityContext with the authenticated [OgiriUser]
 *
 * Configuration properties (from application.yml):
 * - `ogiri.auth.rotate-on-write-only`: Only rotate on mutating requests (POST/PUT/DELETE)
 * - `ogiri.auth.rotate-stale-seconds`: Force rotation if token exceeds this age
 */
open class OgiriTokenAuthenticationFilter(
    private val userDirectory: OgiriUserDirectory,
    private val tokenService: TokenService<*>,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val bypassDecider: AuthenticationBypassDecider,
    private val identifierPolicy: IdentifierPolicy,
    private val properties: OgiriConfigurationProperties,
) : OncePerRequestFilter() {
  private val rotateOnWriteOnly: Boolean
    get() = properties.auth.rotateOnWriteOnly

  private val rotateStaleSeconds: Long
    get() = properties.auth.rotateStaleSeconds

  /**
   * Core filter execution - runs once per HTTP request.
   *
   * **Request lifecycle:**
   * 1. Check if request can skip authentication (public routes, preflight, already authenticated)
   * 2. Extract and validate token from request headers
   * 3. Load user from directory and verify token matches stored hash
   * 4. Detect batch requests and decide whether to rotate token
   * 5. Populate SecurityContext with authenticated user
   * 6. Append refreshed headers if token was rotated
   * 7. Delegate to entry point on authentication failure
   *
   * **Token rotation policy:**
   * - Within batch grace window: Update lastUsedAt only, return no new headers
   * - Outside batch window or forced rotation: Issue new token, return refreshed headers
   * - Force rotation if token age exceeds rotateStaleSeconds (if configured)
   *
   * **Configuration from [OgiriConfigurationProperties]:**
   * - `ogiri.auth.rotate-on-write-only`: Only rotate on POST/PUT/DELETE/PATCH
   * - `ogiri.auth.rotate-stale-seconds`: Force rotation if token exceeds this age
   *
   * @param request HTTP request with authentication headers (access-token, client, uid, expiry)
   * @param response HTTP response where refreshed headers will be appended if token rotated
   * @param filterChain Servlet filter chain to continue processing
   * @throws ServletException if filter processing fails
   * @throws IOException if request/response I/O fails
   */
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

  protected data class AuthResult(val user: OgiriUser, val authHeader: AuthHeader?)

  /**
   * Authenticate a single HTTP request using token headers.
   *
   * **Validation steps:**
   * 1. Extract authentication headers (access-token, client, uid, expiry, access-token-kind)
   * 2. Validate header presence and format
   * 3. Validate client and uid identifiers using [IdentifierPolicy]
   * 4. Verify token is APP token (not sub-token)
   * 5. Load user from [OgiriUserDirectory], throw if not found
   * 6. Validate token hash matches stored token via [TokenService.validToken]
   * 7. Detect batch requests and decide token rotation
   *
   * **Batch detection:**
   * - If request is within batch grace window, only update lastUsedAt, return no new headers
   * - Otherwise, rotate token and return refreshed headers
   *
   * **Error handling:**
   * - Invalid headers or format: Return null (not an authentication attempt)
   * - User not found or token invalid: Throw BadCredentialsException (authentication failure)
   * - These exceptions are caught by doFilterInternal and delegated to AuthenticationEntryPoint
   *
   * @param request HTTP request to authenticate
   * @return AuthResult with user and optionally rotated AuthHeader, or null if not an auth attempt
   * @throws BadCredentialsException if user not found or token invalid
   * @throws BadCredentialsException if client/uid fails identifier validation
   */
  /**
   * Extract authentication headers with fallback to Bearer token parsing.
   *
   * Attempts to extract authentication in the following order:
   * 1. Individual headers: access-token, client, uid, expiry, access-token-kind
   * 2. Cookies: access-token, client, uid, expiry
   * 3. Authorization Bearer header: Base64-encoded JSON with same fields
   *
   * @param request HTTP request
   * @return AuthHeader with parsed values, or empty if no auth found
   */
  protected open fun extractAuthHeaderWithBearer(request: HttpServletRequest): AuthHeader {
    val headerToken = request.extractAuthHeader()
    if (headerToken.isValid()) return headerToken

    // Try Bearer token as fallback
    val authHeader = request.getHeader("Authorization") ?: return headerToken
    val fields = parseBearerToken(authHeader) ?: return headerToken

    return AuthHeader(
        accessToken = fields["access-token"],
        client = fields["client"],
        uid = fields["uid"],
        expiry = fields["expiry"],
        kind = fields["token-type"],
    )
  }

  @Throws(AuthenticationException::class)
  protected open fun authenticateRequest(request: HttpServletRequest): AuthResult? {
    beforeAuth(request)
    val headerToken = extractAuthHeaderWithBearer(request)
    if (!headerToken.isValid()) return null

    val requestStartedAt = Instant.now()
    val client = headerToken.client?.also { validateIdentifier(it, "error.auth.bad_client_id") }!!
    val uid = headerToken.uid?.also { validateIdentifier(it, "error.auth.bad_uid") }!!
    ensureAppToken(headerToken.kind)

    val user =
        userDirectory.loadUserByUsername(uid) as? OgiriUser
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

  /**
   * Decide whether to rotate the token based on request method and staleness.
   *
   * **Decision logic:**
   * 1. If rotateOnWriteOnly=true and request is GET/HEAD: Skip rotation (return null)
   * 2. If rotateStaleSeconds > 0: Check if token exceeds age threshold
   * 3. Otherwise: Always rotate token
   *
   * @param user Authenticated user
   * @param client Client identifier
   * @param method HTTP method (GET, POST, PUT, DELETE, PATCH, etc.)
   * @return New AuthHeader if rotation occurred, null if skipped
   *
   * Configuration:
   * - `ogiri.auth.rotate-on-write-only`: If true, skip rotation for safe methods (GET, HEAD)
   * - `ogiri.auth.rotate-stale-seconds`: If > 0, only rotate if token age exceeds this threshold
   *
   * Example with rotate-on-write-only=true, rotate-stale-seconds=0:
   * - GET /api/data: No rotation (returns null)
   * - POST /api/data: Rotation (returns new AuthHeader)
   * - PUT /api/data: Rotation (returns new AuthHeader)
   */
  private fun rotateTokensIfNeeded(
      user: OgiriUser,
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
    return if (shouldRotate) tokenService.createNewAuthToken(user.getOgiriUserId(), client)
    else null
  }

  private fun isSafeMethod(method: String): Boolean =
      method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

  private fun ensureAppToken(kind: String?) {
    val tokenKind =
        runCatching { TokenType.valueOf(kind?.trim()?.uppercase() ?: "APP") }
            .getOrDefault(TokenType.APP)
    if (tokenKind != TokenType.APP) throw BadCredentialsException("error.auth.bad_token_type")
  }

  private fun validateIdentifier(
      value: String,
      errorCode: String,
  ) {
    if (!identifierPolicy.isValid(value)) throw BadCredentialsException(errorCode)
  }

  private fun buildAuthentication(
      user: OgiriUser,
      request: HttpServletRequest,
  ) =
      UsernamePasswordAuthenticationToken(user, "[PROTECTED_PASSWORD]", user.authorities).apply {
        details = WebAuthenticationDetailsSource().buildDetails(request)
      }
}
