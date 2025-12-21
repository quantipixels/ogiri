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
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenType
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
    private val tokenService: OgiriTokenService<*>,
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
   * Authenticate the incoming HTTP request, populate the SecurityContext, and append refreshed auth
   * headers when tokens rotate.
   *
   * Performs authentication unless the request is exempted by the bypass decider. When
   * authentication succeeds the filter sets the SecurityContext with the authenticated OgiriUser
   * and, if a token rotation produced a new AuthHeader, appends refreshed authentication headers to
   * the response. On authentication failure the security context is cleared and the configured
   * AuthenticationEntryPoint is invoked.
   *
   * @param request HTTP request containing authentication headers or bearer token.
   * @param response HTTP response where rotated authentication headers will be added when
   *   applicable.
   * @param filterChain Filter chain to continue request processing after authentication handling.
   * @throws ServletException if filter processing fails.
   * @throws IOException if reading from or writing to the request/response fails.
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

  /**
   * Invoked before authentication is attempted for the incoming request.
   *
   * Default implementation does nothing; override to perform any preparatory work (for example
   * request inspection, logging, or tracing) before authentication begins.
   *
   * @param request the HTTP request that will be authenticated
   */
  protected open fun beforeAuth(request: HttpServletRequest) {}

  /**
   * Extension point called after an authentication attempt to allow subclasses to react to the
   * result.
   *
   * Implementations may inspect or modify the request and response, perform logging, metrics, or
   * other side effects.
   *
   * @param request The current HTTP request.
   * @param response The current HTTP response.
   * @param authResult The authentication result if authentication succeeded, or `null` if no
   *   authentication was performed.
   */
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
   * 6. Validate token hash matches stored token via [OgiriTokenService.validToken]
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
   * Extract authentication header values, falling back to parsing a Bearer token from the
   * Authorization header.
   *
   * Checks for authentication in this order: individual headers (access-token, client, uid, expiry,
   * token-type), cookies, then a Base64-encoded JSON Bearer token.
   *
   * @return AuthHeader containing parsed authentication fields, or an empty AuthHeader if no
   *   authentication information is found.
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

  /**
   * Authenticates the incoming HTTP request using APP tokens and produces an authentication result.
   *
   * Attempts to extract and validate an auth header (or Bearer token), validate client and uid
   * identifiers, ensure the token kind is APP, load and verify the user, and decide whether to
   * rotate or extend token buffers.
   *
   * @param request The HTTP servlet request to authenticate.
   * @return An AuthResult containing the authenticated OgiriUser and optionally a refreshed
   *   AuthHeader, or `null` if no valid authentication header was present.
   * @throws AuthenticationException If client or uid identifiers are invalid, the token kind is not
   *   APP, the user cannot be loaded, or the access token is invalid.
   */
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
   * Determines whether a new auth token should be issued for the request and returns it when
   * created.
   *
   * Rotation may be suppressed for safe methods when configured, and may be gated by a staleness
   * threshold.
   *
   * @return New `AuthHeader` when a rotation occurs, `null` when no rotation is performed.
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

  /**
   * Determines whether an HTTP method is considered safe (no side effects).
   *
   * @return `true` if the method equals `GET` or `HEAD` (case-insensitive), `false` otherwise.
   */
  private fun isSafeMethod(method: String): Boolean =
      method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

  /**
   * Validates that the provided token kind represents an APP token.
   *
   * @param kind Nullable token kind string; if null a default token type will be used.
   * @throws BadCredentialsException Thrown with message "error.auth.bad_token_type" when the token
   *   kind is not `APP`.
   */
  private fun ensureAppToken(kind: String?) {
    val tokenKind = OgiriTokenType.ofOrDefault(kind)
    if (tokenKind != OgiriTokenType.APP) throw BadCredentialsException("error.auth.bad_token_type")
  }

  /**
   * Validate an identifier string against the configured IdentifierPolicy.
   *
   * @param value The identifier to validate (e.g., client or uid).
   * @param errorCode The error code/message to use when throwing on invalid input.
   * @throws org.springframework.security.authentication.BadCredentialsException if the identifier
   *   is invalid.
   */
  private fun validateIdentifier(
      value: String,
      errorCode: String,
  ) {
    if (!identifierPolicy.isValid(value)) throw BadCredentialsException(errorCode)
  }

  /**
   * Create an Authentication token representing the given user for the current request.
   *
   * @param user The authenticated OgiriUser to set as the principal.
   * @param request The HTTP request used to populate authentication details.
   * @return A `UsernamePasswordAuthenticationToken` with the user's authorities, masked
   *   credentials, and request-derived details.
   */
  private fun buildAuthentication(
      user: OgiriUser,
      request: HttpServletRequest,
  ) =
      UsernamePasswordAuthenticationToken(user, "[PROTECTED_PASSWORD]", user.authorities).apply {
        details = WebAuthenticationDetailsSource().buildDetails(request)
      }
}
