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
package com.quantipixels.ogiri.security.tokens

import com.github.benmanes.caffeine.cache.Caffeine
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.core.JsonCodec
import com.quantipixels.ogiri.security.core.SecurityServiceException
import com.quantipixels.ogiri.security.core.SubTokenHeader
import com.quantipixels.ogiri.security.core.appendAuthHeaders
import com.quantipixels.ogiri.security.core.extractAuthHeader
import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val tokenEqualityCache =
    Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(1, TimeUnit.HOURS).build<
        String, Boolean> { _ ->
      false
    }

private fun tokensMatch(
    tokenHash: String,
    token: String,
    passwordEncoder: PasswordEncoder,
): Boolean {
  val key = "$tokenHash/$token"
  val cached = tokenEqualityCache.getIfPresent(key)
  if (cached != null) {
    return cached
  }
  val result = passwordEncoder.matches(token, tokenHash)
  tokenEqualityCache.put(key, result)
  return result
}

data class OgiriGeneratedTokens<T : OgiriToken>(
    val appToken: T,
    val subTokens: Map<String, T>,
)

/**
 * Core token orchestration: issues/rotates APP tokens, manages pluggable sub-tokens, and validates
 * tokens for authentication and downstream protocols.
 *
 * This service works with any token implementation of OgiriToken. Subclasses can override
 * tokenFactory() to customize token creation if needed, or supply tokens directly through the
 * public API methods.
 */
@Service
open class OgiriTokenService<T : OgiriToken>(
    private val repository: OgiriTokenRepository<T>,
    private val passwordEncoder: PasswordEncoder,
    private val userDirectory: OgiriUserDirectory,
    private val identifierPolicy: IdentifierPolicy,
    private val subTokenRegistry: OgiriSubTokenRegistry,
    protected val properties: OgiriConfigurationProperties,
) {
  private val maxClients: Long = properties.auth.maxClients
  private val batchGraceSeconds: Long = properties.auth.batchGraceSeconds
  private val tokenLifespanDays: Long = properties.auth.tokenLifespanDays
  /**
   * Factory function for creating new token instances.
   *
   * This method can be overridden by subclasses to provide custom token creation logic. Users can
   * extend OgiriTokenService and override this method to instantiate their custom OgiriToken
   * implementations.
   *
   * Example:
   * ```kotlin
   * class MyTokenService<T : OgiriToken>(
   *   repository: OgiriTokenRepository<T>,
   *   // ... other dependencies
   * ) : OgiriTokenService<T>(repository, ...) {
   *   override fun tokenFactory(
   *     userId: Long,
   *     client: String,
   *     hashedToken: String,
   *     tokenType: OgiriTokenType,
   *     expiry: Instant,
   *     tokenSubtype: String?,
   *     plainTokenValue: String,
   *   ): T = MyToken(
   *     userId = userId,
   *     client = client,
   *     token = hashedToken,
   *     tokenType = tokenType,
   *     tokenSubtype = tokenSubtype,
   *     expiryAt = expiry,
   *     plainToken = plainTokenValue
   *   ) as T
   * }
   * ```
   *
   * @param userId The user ID for the token
   * @param client The client/application identifier
   * @param hashedToken The hashed (encoded) token value
   * @param tokenType The token type (APP or SUB)
   * @param expiry The token expiration time
   * @param tokenSubtype Optional sub-token type (e.g., "device", "chat")
   * @param plainTokenValue The plain (unhashed) token value for temporary in-memory use
   * @return A new token instance configured with the provided values
   */
  @Suppress("UNCHECKED_CAST")
  protected open fun tokenFactory(
      userId: Long,
      client: String,
      hashedToken: String,
      tokenType: OgiriTokenType,
      expiry: Instant,
      tokenSubtype: String?,
      plainTokenValue: String,
  ): T {
    // Default factory throws informative error - subclass must override
    throw UnsupportedOperationException(
        "TokenService.tokenFactory() must be overridden by subclass. " +
            "Extend TokenService and provide your token implementation. " +
            "See class documentation for example implementation.",
    )
  }

  @Transactional(readOnly = true)
  fun getAllByUserId(userId: Long): List<T> = repository.findAllByUserId(userId)

  @Transactional(readOnly = true)
  fun getByUserIdAndClient(
      userId: Long,
      client: String,
  ): T? = repository.findByUserIdAndClient(userId, client)

  @Transactional
  fun deleteToken(
      userId: Long,
      client: String,
  ) {
    repository.deleteByUserIdAndClient(userId, client)
  }

  @Transactional
  fun deleteToken(
      userId: Long,
      clients: Collection<String>,
  ) {
    if (clients.isEmpty()) return
    repository.deleteByUserIdAndClientIn(userId, clients)
  }

  @Transactional
  fun deleteAllForUser(userId: Long) {
    repository.deleteByUserId(userId)
  }

  @Transactional
  fun cleanupExpiredTokens(now: Instant = Instant.now()): Int {
    val expired = repository.findByExpiryAtBefore(now)
    expired.forEach { repository.delete(it) }
    return expired.size
  }

  @Transactional(readOnly = true)
  fun isBatchRequest(
      user: OgiriUser,
      client: String,
      requestStartedAt: Instant,
  ): Boolean =
      getByUserIdAndClient(user.getOgiriUserId(), client)
          ?.takeIf { !it.isExpired() }
          ?.updatedAt
          ?.isAfter(requestStartedAt.minusSeconds(batchGraceSeconds))
          ?: false

  @Transactional
  fun extendBatchBuffer(
      user: OgiriUser,
      token: String,
      client: String,
  ): AuthHeader? {
    val clientToken = getByUserIdAndClient(user.getOgiriUserId(), client) ?: return null
    clientToken.lastUsedAt = Instant.now()
    repository.save(clientToken)
    return updateAuthHeader(user, token, client)
  }

  private fun clientForSubToken(
      reg: OgiriSubTokenRegistration,
      parentClient: String,
  ): String = reg.clientIdFor(parentClient)

  @Transactional
  @JvmOverloads
  fun createOrUpdateToken(
      user: OgiriUser,
      client: String?,
      expiry: Instant,
      tokenType: OgiriTokenType = OgiriTokenType.APP,
      tokenSubtype: String? = null,
  ): T {
    val tokenClient = client ?: identifierPolicy.generate()
    val generatedToken = identifierPolicy.generate()
    val hashedToken = passwordEncoder.encode(generatedToken)
    var token = client?.let { getByUserIdAndClient(user.getOgiriUserId(), it) }
    if (token == null) {
      token =
          tokenFactory(
              userId = user.getOgiriUserId(),
              client = tokenClient,
              hashedToken = hashedToken,
              tokenType = tokenType,
              tokenSubtype = tokenSubtype,
              expiry = expiry,
              plainTokenValue = generatedToken,
          )
      token.tokenUpdatedAt = Instant.now()
      token.plainToken = generatedToken
    }
    token.apply {
      expiryAt = expiry
      if (tokenType == OgiriTokenType.APP) {
        if (id != 0L) {
          previousToken = lastToken
          lastToken = this.token
        }
        this.token = hashedToken
        tokenUpdatedAt = Instant.now()
        plainToken = generatedToken
      } else {
        this.token = hashedToken
        tokenUpdatedAt = Instant.now()
        plainToken = generatedToken
      }
    }
    return repository.save(token)
  }

  @Transactional
  fun createToken(
      user: OgiriUser,
      client: String?,
  ): OgiriGeneratedTokens<T> {
    val expiry = Instant.now().plus(tokenLifespanDays, ChronoUnit.DAYS)
    val appToken = createOrUpdateToken(user, client, expiry)
    val subTokens = issueSubTokens(user, appToken.client, null, forceNew = false)
    cleanOldTokens(user)
    return OgiriGeneratedTokens(appToken, subTokens)
  }

  /**
   * Issue sub-tokens for a parent client.
   *
   * @param requestedNames optional whitelist of sub-token names; when null, defaults are issued.
   * @param forceNew when true, existing sub-tokens are rotated even if the registration is
   *   forceNew=false.
   */
  @Transactional
  fun issueSubTokens(
      user: OgiriUser,
      parentClient: String,
      requestedNames: Collection<String>?,
      forceNew: Boolean,
  ): Map<String, T> {
    val registrations =
        subTokenRegistry.registrations().filter {
          requestedNames?.let { names -> it.name in names } ?: it.includeByDefault
        }
    val results = mutableMapOf<String, T>()
    val parentToken = getByUserIdAndClient(user.getOgiriUserId(), parentClient)
    val parentExpiry =
        parentToken?.expiryAt ?: Instant.now().plus(tokenLifespanDays, ChronoUnit.DAYS)
    registrations.forEach { reg ->
      val subClient = clientForSubToken(reg, parentClient)
      val expiry = reg.expiry(parentExpiry)
      val existing =
          if (!forceNew && !reg.forceNew) getByUserIdAndClient(user.getOgiriUserId(), subClient)
          else null
      val token =
          existing
              ?: createOrUpdateToken(
                  user = user,
                  client = subClient,
                  expiry = expiry,
                  tokenType = OgiriTokenType.SUB,
                  tokenSubtype = reg.name,
              )
      results[reg.name] = token
    }
    return results
  }

  /**
   * Create a new authentication token for a user and client.
   *
   * This is the primary public API for token issuance. It performs the following:
   * 1. Loads the user from OgiriUserDirectory, throws SecurityServiceException if not found
   * 2. Generates a new APP token with expiry based on [tokenLifespanDays]
   * 3. Issues all sub-tokens marked with includeByDefault=true
   * 4. Enforces [maxClients] limit by removing oldest tokens if exceeded
   * 5. Returns AuthHeader with APP token and all sub-tokens serialized for HTTP response
   *
   * The returned AuthHeader should be appended to the HTTP response via [appendAuthHeaders], which
   * will set response headers: access-token, client, uid, expiry, sub-tokens
   *
   * @param userId User identifier to create token for
   * @param client Optional client identifier. If null, a random identifier is generated.
   * @return AuthHeader containing access-token, client, uid, expiry, and sub-tokens
   * @throws SecurityServiceException if user not found ("user.not_found") or other auth failures
   *
   * Example:
   * ```kotlin
   * val authHeader = tokenService.createNewAuthToken(userId = 123L, client = "web-app")
   * response.appendAuthHeaders(authHeader)  // Sets response headers
   * ```
   */
  @Transactional
  fun createNewAuthToken(
      userId: Long,
      client: String?,
  ): AuthHeader {
    val user = userDirectory.findById(userId) ?: throw SecurityServiceException("user.not_found")
    val generatedTokens = createToken(user, client)
    return updateAuthHeader(
        user = user,
        token = generatedTokens.appToken.plainToken!!,
        client = generatedTokens.appToken.client,
        issuedSubTokens = generatedTokens.subTokens,
    )
  }

  /**
   * Enforce token limits and clean up orphaned tokens for a user.
   *
   * This method performs two critical cleanup operations:
   * 1. **Orphan Cleanup**: Removes sub-tokens whose parent APP tokens no longer exist (e.g., if an
   *    APP token was manually deleted but its chat/device sub-tokens remain)
   * 2. **Max Clients Enforcement**: Removes oldest APP tokens when [maxClients] limit is exceeded
   *     - Tracks usage via lastUsedAt and updatedAt to identify least-recently-used tokens
   *     - Cascade-deletes corresponding sub-tokens when APP token is removed
   *
   * Called automatically after token creation to enforce limits. Can be called manually to reclaim
   * storage from unused tokens.
   *
   * @param user User whose tokens should be cleaned
   *
   * Token selection for removal (when exceeding maxClients):
   * - Sorts by lastUsedAt (with fallback to updatedAt if lastUsedAt is null)
   * - Removes oldest tokens first (least recently used)
   * - Deletes all associated sub-tokens when parent APP token is removed
   *
   * Example: If maxClients=5 and user has 7 APP tokens, the 2 oldest are deleted along with all
   * their device/chat/api sub-tokens.
   */
  @Transactional
  fun cleanOldTokens(user: OgiriUser) {
    val tokens = repository.findAllByUserId(user.getOgiriUserId())
    if (tokens.isEmpty()) return

    val registrations = subTokenRegistry.registrations()
    val (appTokens, subTokens) =
        tokens.partition { OgiriTokenType.of(it.tokenType) == OgiriTokenType.APP }

    // Remove orphaned sub-tokens (sub-tokens without corresponding app token)
    val appClients = appTokens.clientIds()
    val expectedSubClients = expectedSubClientsFor(appClients, registrations)
    subTokens.filterOutClientIds(expectedSubClients).forEach { repository.delete(it) }

    // Enforce max clients limit on app tokens
    if (appTokens.size > maxClients) {
      val toRemoveCount = (appTokens.size - maxClients).toInt()
      val sorted =
          appTokens.sortedWith(
              compareBy<OgiriToken> { it.lastUsedAt ?: it.updatedAt }.thenBy { it.updatedAt },
          )
      val remove = sorted.take(toRemoveCount)
      remove.forEach { repository.delete(it) }
      val removeSubClients = generateSubClientIds(remove.clientIds(), registrations)
      deleteToken(user.getOgiriUserId(), removeSubClients)
    }
  }

  /**
   * Validate if a plain token matches the stored token for a user/client combination.
   *
   * This method checks token validity in the following order:
   * 1. Verify token exists in database for this user/client
   * 2. Check if token matches the current token (account for recent rotation)
   * 3. Allow grace-period reuse of the previous token within [batchGraceSeconds]
   *
   * The grace period allows legitimate batch requests (multiple simultaneous API calls) to succeed
   * even if they arrive after token rotation has started.
   *
   * @param plainToken The plain token value from HTTP headers to validate
   * @param user Authenticated user object
   * @param client Client identifier associated with the token
   * @return true if token is valid and matches stored token, false otherwise
   *
   * Used by OgiriTokenAuthenticationFilter during authentication. Does not throw exceptions;
   * authentication failures are delegated to AuthenticationEntryPoint.
   */
  @Transactional(readOnly = true)
  fun validToken(
      plainToken: String,
      user: OgiriUser,
      client: String,
  ): Boolean {
    val token = getByUserIdAndClient(user.getOgiriUserId(), client) ?: return false
    if (tokenIsCurrent(plainToken, token)) return true
    return tokenCanBeReused(plainToken, token)
  }

  private fun tokenIsCurrent(
      plainToken: String,
      token: T,
  ): Boolean {
    val tokenHash = token.token
    val previousTokenHash = token.previousToken
    if (token.isExpired()) return false
    return doesTokenMatch(tokenHash, plainToken) || doesTokenMatch(previousTokenHash, plainToken)
  }

  private fun doesTokenMatch(
      hashedToken: String?,
      plainToken: String?,
  ): Boolean {
    if (hashedToken.isNullOrBlank() || plainToken.isNullOrBlank()) return false
    return tokensMatch(hashedToken, plainToken, passwordEncoder)
  }

  private fun tokenCanBeReused(
      plainToken: String,
      token: T,
  ): Boolean {
    val lastTokenHash = token.lastToken ?: return false
    val gracePeriodThreshold = Instant.now().minusSeconds(batchGraceSeconds)
    if (token.updatedAt.isBefore(gracePeriodThreshold)) return false
    return doesTokenMatch(lastTokenHash, plainToken)
  }

  @Transactional(readOnly = true)
  fun buildAuthHeader(
      user: OgiriUser,
      token: String,
      client: String,
      issuedSubTokens: Map<String, T>? = null,
  ): AuthHeader {
    val appToken = getByUserIdAndClient(user.getOgiriUserId(), client)
    val subHeaders = mutableMapOf<String, SubTokenHeader>()
    subTokenRegistry.registrations().forEach { reg ->
      val subClient = reg.clientIdFor(client)
      val provided = issuedSubTokens?.get(reg.name)
      val stored = getByUserIdAndClient(user.getOgiriUserId(), subClient)
      val chosen = provided ?: stored
      val plain = provided?.plainToken ?: chosen?.plainToken
      val expiry = chosen?.expiryAt?.toString()
      subHeaders[reg.name] =
          SubTokenHeader(
              client = subClient,
              token = plain,
              expiry = expiry,
          )
    }
    val filteredSubHeaders =
        subHeaders.filterValues { !it.client.isNullOrBlank() || !it.token.isNullOrBlank() }
    return AuthHeader(
        accessToken = token,
        client = client,
        uid = user.username,
        expiry = appToken?.expiryAt.toString(),
        kind = OgiriTokenType.APP.label,
        subTokens = filteredSubHeaders.ifEmpty { null },
    )
  }

  @Transactional
  fun updateAuthHeader(
      user: OgiriUser,
      token: String,
      client: String,
      issuedSubTokens: Map<String, T>? = null,
  ): AuthHeader {
    val authHeaders = buildAuthHeader(user, token, client, issuedSubTokens)
    cleanOldTokens(user)
    return authHeaders
  }

  @Transactional(readOnly = true)
  fun shouldRotate(
      user: OgiriUser,
      client: String,
      thresholdSeconds: Long,
  ): Boolean {
    if (thresholdSeconds <= 0) return true
    val token = getByUserIdAndClient(user.getOgiriUserId(), client) ?: return true
    val cutoff = Instant.now().minusSeconds(thresholdSeconds)
    return token.tokenUpdatedAt.isBefore(cutoff)
  }

  @Transactional
  fun verifyUser(
      request: HttpServletRequest,
      response: HttpServletResponse,
      email: String,
      password: String,
  ) {
    val user =
        userDirectory.findByEmail(email)
            ?: throw SecurityServiceException("error.auth.invalid_credentials")
    val matches = passwordEncoder.matches(password, user.password)
    if (!matches) throw SecurityServiceException("error.auth.invalid_credentials")

    val authentication =
        UsernamePasswordAuthenticationToken(user, "[PROTECTED_PASSWORD]", user.authorities)
    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
    SecurityContextHolder.getContext().authentication = authentication

    val authHeaders = createNewAuthToken(user.getOgiriUserId(), null)
    response.appendAuthHeaders(authHeaders)
    userDirectory.recordSuccessfulLogin(user.getOgiriUserId())
  }

  @Transactional
  fun revokeClient(
      userId: Long,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ) {
    val user = userDirectory.findById(userId) ?: return
    val authHeader = request.extractAuthHeader()
    val clientId = authHeader.client ?: return
    val token = getByUserIdAndClient(user.getOgiriUserId(), clientId)
    val isTokenValid = token?.let { doesTokenMatch(it.token, authHeader.accessToken) } ?: false

    if (isTokenValid) {
      deleteToken(user.getOgiriUserId(), clientId)
      val subClients =
          subTokenRegistry.registrations().map { registration ->
            registration.clientIdFor(clientId)
          }
      deleteToken(user.getOgiriUserId(), subClients)
      val authHeaders = buildAuthHeader(user, authHeader.accessToken!!, clientId, null)
      response.appendAuthHeaders(authHeaders)
    }
  }

  /** Force rotation of a specific sub-token and return fresh headers. */
  @Transactional
  fun renewSubToken(
      userId: Long,
      request: HttpServletRequest,
      response: HttpServletResponse,
      name: String,
  ) {
    val currentUser =
        userDirectory.findById(userId) ?: throw SecurityServiceException("user.not_found")
    val authHeader = request.extractAuthHeader()

    val clientId = authHeader.client ?: throw SecurityServiceException("error.auth.missing_token")
    val parent =
        getByUserIdAndClient(currentUser.getOgiriUserId(), clientId)
            ?: throw SecurityServiceException("error.auth.missing_token")
    if (parent.isExpired()) throw SecurityServiceException("error.auth.bad_credentials")

    val issued =
        issueSubTokens(
            user = currentUser,
            parentClient = clientId,
            requestedNames = setOf(name),
            forceNew = true,
        )
    val authHeaders =
        buildAuthHeader(
            currentUser,
            authHeader.accessToken!!,
            clientId,
            issuedSubTokens = issued,
        )
    response.appendAuthHeaders(authHeaders)
  }

  fun tryDecodeSubBearer(encodedPart: String): SubTokenHeader? {
    return try {
      val json = String(Base64.getDecoder().decode(encodedPart), Charsets.UTF_8)
      JsonCodec.mapper.readValue(json, Map::class.java)?.let { map ->
        @Suppress("UNCHECKED_CAST") val values = map as Map<String, Any?>
        SubTokenHeader(
            client = values["client"] as? String,
            token = values["token"] as? String,
            expiry = values["expiry"] as? String,
        )
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun tokenMatches(
      token: T,
      plain: String,
  ): Boolean = !token.expiryAt.isBefore(Instant.now()) && doesTokenMatch(token.token, plain)

  /**
   * Validate a sub-token for a user.
   *
   * Accepts either a raw token or a base64-encoded bearer JSON containing `client`, `token`,
   * optional `expiry`. Expiry is always verified server-side.
   */
  @Transactional
  fun validateSubToken(
      username: String,
      subTokenName: String,
      rawOrBearer: String,
  ): Boolean {
    val user = userDirectory.findByUsername(username) ?: return false
    val registration =
        subTokenRegistry.registrations().find { it.name == subTokenName } ?: return false
    val tokenField = rawOrBearer.trim()
    val authHeader = tryDecodeSubBearer(tokenField.removePrefix("Bearer ").trim())

    if (authHeader != null &&
        !authHeader.token.isNullOrBlank() &&
        !authHeader.client.isNullOrBlank()) {
      val token =
          getByUserIdAndClient(user.getOgiriUserId(), authHeader.client)?.takeIf {
            OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB && it.tokenSubtype == subTokenName
          }
              ?: return false
      return tokenMatches(token, authHeader.token) && registration.validate(authHeader.token)
    }

    val all =
        repository.findAllByUserIdAndTokenSubtype(user.getOgiriUserId(), subTokenName).filter {
          OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB
        }
    return all.any { tokenMatches(it, tokenField) && registration.validate(tokenField) }
  }

  /**
   * Retrieve a sub-token for a user by name.
   *
   * Returns the raw [OgiriToken] so consumers that only know the generic contract can inspect the
   * stored hash, expiry, or client identifiers without depending on a concrete token class.
   *
   * @param userId User ID
   * @param subtype Sub-token identifier (e.g., "device", "chat", "notification")
   * @return The sub-token entity, or null if not found, expired, or not registered
   */
  @Transactional(readOnly = true)
  fun getSubToken(
      userId: Long,
      subtype: String,
  ): OgiriToken? {
    val registration = subTokenRegistry.registrations().find { it.name == subtype } ?: return null

    return repository.findAllByUserIdAndTokenSubtype(userId, subtype).firstOrNull { token ->
      OgiriTokenType.of(token.tokenType) == OgiriTokenType.SUB
    }
  }

  /**
   * Revoke all sub-tokens of a specific type for a user.
   *
   * This removes all tokens where tokenSubtype matches the given name, regardless of parent client.
   * Useful for revoking device sessions, chat credentials, etc.
   *
   * @param userId User ID
   * @param subtypeName Sub-token type to revoke (e.g., "device", "chat")
   * @return true if at least one token was revoked, false if none existed
   */
  @Transactional
  fun revokeSubToken(
      userId: Long,
      subtypeName: String,
  ): Boolean {
    val tokens =
        repository.findAllByUserIdAndTokenSubtype(userId, subtypeName).filter {
          OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB
        }

    if (tokens.isEmpty()) return false

    tokens.forEach { repository.delete(it) }
    return true
  }

  /**
   * Renew a specific sub-token and return new headers containing only that sub-token.
   *
   * The returned [AuthHeader] omits the APP token so consumers can append the new sub-token to
   * downstream protocols without rotating the parent client. Returns null when the user/client is
   * missing, the parent token is expired, or the subtype is not registered.
   */
  @Transactional
  fun renewSubToken(
      userId: Long,
      parentClient: String,
      subtypeName: String,
  ): AuthHeader? {
    val user = userDirectory.findById(userId) ?: return null
    val parentToken = getByUserIdAndClient(userId, parentClient) ?: return null
    if (parentToken.isExpired()) return null

    val registration =
        subTokenRegistry.registrations().find { it.name == subtypeName } ?: return null
    val subClient = registration.clientIdFor(parentClient)

    val newSubToken =
        createOrUpdateToken(
            user = user,
            client = subClient,
            expiry = registration.expiry(parentToken.expiryAt),
            tokenType = OgiriTokenType.SUB,
            tokenSubtype = subtypeName,
        )

    val plain = newSubToken.plainToken ?: return null
    val subHeader =
        SubTokenHeader(
            client = subClient,
            token = plain,
            expiry = newSubToken.expiryAt.toString(),
        )
    return AuthHeader(subTokens = mapOf(subtypeName to subHeader))
  }

  /**
   * Generate expected sub-client IDs from app client IDs and registrations.
   *
   * @param appClients Set of app client IDs
   * @param registrations Sub-token registrations
   * @return Set of expected sub-client IDs
   */
  private fun expectedSubClientsFor(
      appClients: Set<String>,
      registrations: List<OgiriSubTokenRegistration>,
  ): Set<String> =
      appClients.flatMap { parent -> registrations.map { it.clientIdFor(parent) } }.toSet()

  /**
   * Generate sub-client IDs from app client IDs and registrations.
   *
   * @param appClients Set of app client IDs
   * @param registrations Sub-token registrations
   * @return List of sub-client IDs
   */
  private fun generateSubClientIds(
      appClients: Set<String>,
      registrations: List<OgiriSubTokenRegistration>,
  ): List<String> = appClients.flatMap { parent -> registrations.map { it.clientIdFor(parent) } }
}
