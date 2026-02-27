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

import com.fasterxml.jackson.core.JsonProcessingException
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.core.JsonCodec
import com.quantipixels.ogiri.security.core.OgiriService
import com.quantipixels.ogiri.security.core.SecurityServiceException
import com.quantipixels.ogiri.security.core.SubTokenHeader
import com.quantipixels.ogiri.security.core.appendAuthHeaders
import com.quantipixels.ogiri.security.core.extractAuthHeader
import com.quantipixels.ogiri.security.spi.NoOpOgiriAuditHook
import com.quantipixels.ogiri.security.spi.NoOpOgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriRateLimitHook
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Threshold ratio for triggering token cleanup.
 *
 * Token cleanup is only performed when the number of tokens reaches this percentage of maxClients.
 * This prevents unnecessary database operations when users have few active tokens.
 */
const val CLEANUP_THRESHOLD_RATIO = 0.8

/**
 * Result of a token creation operation.
 *
 * @property appToken The newly issued APP token. [OgiriToken.plainToken] is populated here and
 *   should be sent to the client; it is `null` after a round-trip from the database.
 * @property subTokens Map of sub-token registration name → issued sub-token. Empty when no
 *   sub-token registrations are active. Keys match [OgiriSubTokenRegistration.name], not client
 *   IDs.
 */
data class OgiriGeneratedTokens<T : OgiriToken>(
    val appToken: T,
    val subTokens: Map<String, T>,
)

/**
 * Core token orchestration: issues/rotates APP tokens, manages pluggable sub-tokens, and validates
 * tokens for authentication and downstream protocols.
 *
 * This service works with any [OgiriToken] implementation. Extend this class and override
 * [tokenFactory] to instantiate your custom token class. Inject the six required collaborators;
 * optional extension points are passed directly via the constructor or wired by the
 * auto-configuration.
 *
 * ```kotlin
 * @Service
 * class MyTokenService(
 *     repo: OgiriTokenRepository<MyToken>,
 *     passwordEncoder: PasswordEncoder,
 *     userDirectory: OgiriUserDirectory,
 *     identifierPolicy: IdentifierPolicy,
 *     subTokenRegistry: OgiriSubTokenRegistry,
 *     properties: OgiriConfigurationProperties,
 * ) : OgiriTokenService<MyToken>(repo, passwordEncoder, userDirectory,
 *                                identifierPolicy, subTokenRegistry, properties) {
 *     override fun tokenFactory(...): MyToken = MyToken(...)
 * }
 * ```
 *
 * Optional extension points (all default to no-op / null):
 * - Call [setAuditHook] to receive audit events.
 * - Call [setRateLimitHook] to enforce rate limits.
 * - Call [setLookupCache] to enable token caching (or add `ogiri-caffeine` / `ogiri-redis`).
 *
 * The auto-configuration wires these via `ObjectProvider.ifAvailable` after construction. Callers
 * that extend this class directly simply call the setters on `this` before the service is first
 * used.
 */
@OgiriService
open class OgiriTokenService<T : OgiriToken>
constructor(
    private val repository: OgiriTokenRepository<T>,
    private val passwordEncoder: PasswordEncoder,
    private val userDirectory: OgiriUserDirectory,
    private val identifierPolicy: IdentifierPolicy,
    private val subTokenRegistry: OgiriSubTokenRegistry,
    protected val properties: OgiriConfigurationProperties,
) {
  private var auditHook: OgiriAuditHook = NoOpOgiriAuditHook
  private var rateLimitHook: OgiriRateLimitHook = NoOpOgiriRateLimitHook
  private var lookupCache: OgiriTokenLookupCache<T>? = null

  /** Replaces the audit hook. Defaults to [NoOpOgiriAuditHook] when not called. */
  open fun setAuditHook(hook: OgiriAuditHook) {
    this.auditHook = hook
  }

  /** Replaces the rate-limit hook. Defaults to [NoOpOgiriRateLimitHook] when not called. */
  open fun setRateLimitHook(hook: OgiriRateLimitHook) {
    this.rateLimitHook = hook
  }

  /**
   * Wires the lookup cache. When not called the service falls through to the repository on every
   * read — identical behaviour to having no cache configured.
   */
  open fun setLookupCache(cache: OgiriTokenLookupCache<T>) {
    this.lookupCache = cache
  }

  private val maxClients: Long = properties.auth.maxClients
  private val batchGraceSeconds: Long = properties.auth.batchGraceSeconds
  private val tokenLifespanDays: Long = properties.auth.tokenLifespanDays

  /**
   * Cache for token comparison results to avoid repeated BCrypt operations.
   *
   * Uses configuration from [OgiriConfigurationProperties.CacheProperties] for size and expiry.
   */
  private val tokenEqualityCache: Cache<String, Boolean> by lazy {
    Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterWrite(properties.cache.expiryMinutes, TimeUnit.MINUTES)
        .build()
  }

  /**
   * Cache for batch request detection timestamps to avoid database queries.
   *
   * Stores the last update timestamp for each user:client combination. Entries expire slightly
   * after the batch grace period to ensure stale entries don't interfere with detection.
   */
  private val batchTimestampCache: Cache<String, Instant> by lazy {
    Caffeine.newBuilder()
        .maximumSize(properties.cache.maxSize)
        .expireAfterWrite(batchGraceSeconds + 1, TimeUnit.SECONDS)
        .build()
  }

  /**
   * Look up a token by userId/client, checking [lookupCache] before the repository.
   *
   * On a cache miss the result is stored so subsequent reads within the same cache window skip the
   * DB entirely.
   */
  private fun lookupToken(userId: Long, client: String): T? {
    lookupCache?.get(userId, client)?.let {
      return it
    }
    val token = repository.findByUserIdAndClient(userId, client).orElse(null)
    token?.let { lookupCache?.put(userId, client, it) }
    return token
  }

  private fun evictFromCache(userId: Long, client: String) {
    lookupCache?.evict(userId, client)
  }

  private fun evictAllFromCache(userId: Long) {
    lookupCache?.evictAll(userId)
  }

  private fun batchCacheKey(
      userId: Long,
      client: String,
  ): String = "$userId:$client"

  /**
   * Cached sub-token registrations.
   *
   * Registrations are retrieved once at first access and cached for the service's lifetime,
   * avoiding repeated calls to [OgiriSubTokenRegistry.registrations].
   */
  private val cachedRegistrations: List<OgiriSubTokenRegistration> by lazy {
    subTokenRegistry.registrations()
  }

  /**
   * Check if a plain token matches a hashed token, using cache to avoid repeated BCrypt operations.
   *
   * Implements timing attack protection by ensuring a minimum delay to mask cache hit vs miss
   * timing. Cache keys are hashed to prevent plaintext token extraction from memory dumps.
   *
   * @param tokenHash The BCrypt-hashed token from the database
   * @param token The plaintext token to validate
   * @return true if tokens match, false otherwise
   */
  private fun tokensMatch(
      tokenHash: String,
      token: String,
  ): Boolean {
    val key = "${tokenHash.length}:${sha256(tokenHash)}:${token.length}:${sha256(token)}"
    val startTime = System.nanoTime()

    val result = tokenEqualityCache.get(key) { passwordEncoder.matches(token, tokenHash) }

    // Ensure minimum 100ms to mask cache hit vs miss timing
    val elapsed = System.nanoTime() - startTime
    val minDelayNanos = 100_000_000L // 100ms
    if (elapsed < minDelayNanos) {
      Thread.sleep((minDelayNanos - elapsed) / 1_000_000)
    }

    return result
  }

  /**
   * Compute SHA-256 hash for cache key generation.
   *
   * @param input The string to hash
   * @return Hex-encoded SHA-256 hash
   */
  private fun sha256(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray()).fold("") { str, byte -> str + "%02x".format(byte) }
  }

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
    throw UnsupportedOperationException(
        "TokenService.tokenFactory() must be overridden by subclass. " +
            "Extend TokenService and provide your token implementation. " +
            "See class documentation for example implementation.",
    )
  }

  /**
   * Returns all tokens (APP and SUB) for [userId], ordered by most recently updated first. Expired
   * tokens are included; filter on [OgiriToken.isExpired] if needed.
   */
  @Transactional(readOnly = true)
  fun getAllByUserId(userId: Long): List<T> = repository.findByUserIdOrderByUpdatedAtDesc(userId)

  /**
   * Returns the token for [userId]/[client], or `null` if none exists. Expired tokens are returned;
   * check [OgiriToken.isExpired] if currency matters.
   */
  @Transactional(readOnly = true)
  fun getByUserIdAndClient(
      userId: Long,
      client: String,
  ): T? = repository.findByUserIdAndClient(userId, client).orElse(null)

  /**
   * Deletes the token for [userId]/[client] and evicts it from the lookup cache. Silent no-op if no
   * matching token exists. Does not cascade to sub-tokens.
   */
  @Transactional
  fun deleteToken(
      userId: Long,
      client: String,
  ) {
    repository.deleteByUserIdAndClient(userId, client)
    evictFromCache(userId, client)
  }

  /**
   * Deletes tokens for [userId] matching any of the given [clients] and evicts them from the lookup
   * cache. Silent no-op for any client IDs that have no matching token. Does not cascade to
   * sub-tokens.
   */
  @Transactional
  fun deleteToken(
      userId: Long,
      clients: Collection<String>,
  ) {
    if (clients.isEmpty()) return
    repository.deleteByUserIdAndClientIn(userId, clients)
    clients.forEach { evictFromCache(userId, it) }
  }

  /**
   * Deletes all tokens (APP and SUB) for [userId] and evicts all user entries from the lookup
   * cache.
   */
  @Transactional
  fun deleteAllForUser(userId: Long) {
    repository.deleteByUserId(userId)
    evictAllFromCache(userId)
  }

  /**
   * Deletes all expired tokens in a single bulk DELETE.
   *
   * For large token tables prefer [cleanupExpiredTokensBatched] to avoid long table locks.
   *
   * @param now Cutoff instant; tokens with `expiryAt` before this value are deleted.
   * @return Number of tokens deleted.
   */
  @Transactional
  fun cleanupExpiredTokens(now: Instant = Instant.now()): Int =
      repository.deleteByExpiryAtBefore(now)

  /**
   * Clean up expired tokens in batches.
   *
   * This method repeatedly deletes batches of expired tokens until none remain, using the batch
   * size from configuration. This is more efficient for large-scale cleanup as it avoids
   * overwhelming the database with a single large DELETE operation.
   *
   * @param now The cutoff time for expiry comparison. Tokens with expiryAt before this are deleted.
   * @return Total number of tokens deleted across all batches.
   */
  @Transactional
  fun cleanupExpiredTokensBatched(now: Instant = Instant.now()): Int {
    val batchSize = properties.cleanup.batchSize
    var totalDeleted = 0
    var deleted: Int
    do {
      val expired = repository.findByExpiryAtBefore(now).take(batchSize)
      expired.forEach { repository.delete(it) }
      deleted = expired.size
      totalDeleted += deleted
    } while (deleted == batchSize)
    return totalDeleted
  }

  /**
   * Returns `true` when the [user]/[client] token was last updated within [batchGraceSeconds] of
   * [requestStartedAt], indicating that a concurrent batch of requests is in flight.
   *
   * Batch requests should call [extendBatchBuffer] instead of triggering a full token rotation.
   * Checks the in-memory timestamp cache before falling back to the database.
   *
   * @param requestStartedAt The instant at which the current request began (caller's clock).
   */
  @Transactional(readOnly = true)
  fun isBatchRequest(
      user: OgiriUser,
      client: String,
      requestStartedAt: Instant,
  ): Boolean {
    val userId = user.getOgiriUserId()
    val cacheKey = batchCacheKey(userId, client)

    val cachedTimestamp = batchTimestampCache.getIfPresent(cacheKey)
    if (cachedTimestamp != null) {
      val threshold = requestStartedAt.minusSeconds(batchGraceSeconds)
      return cachedTimestamp.isAfter(threshold) || cachedTimestamp == threshold
    }

    // Cache miss - query database (lookupToken populates entity cache on miss)
    val token = lookupToken(userId, client)
    val updatedAt = token?.takeIf { !it.isExpired() }?.updatedAt ?: return false

    batchTimestampCache.put(cacheKey, updatedAt)
    val threshold = requestStartedAt.minusSeconds(batchGraceSeconds)
    return updatedAt.isAfter(threshold) || updatedAt == threshold
  }

  /**
   * Refreshes the batch window for a user/client without rotating the token.
   *
   * Writes `lastUsedAt` to the database only when the stored value is older than half of
   * [batchGraceSeconds], reducing write pressure during high-frequency batch requests.
   *
   * @param token Plain (unhashed) access-token value from the request headers.
   * @return Refreshed [AuthHeader], or `null` if no token exists for this user/client.
   */
  @Transactional
  fun extendBatchBuffer(
      user: OgiriUser,
      token: String,
      client: String,
  ): AuthHeader? {
    val userId = user.getOgiriUserId()
    val clientToken = lookupToken(userId, client) ?: return null
    val now = Instant.now()

    // Only update database if timestamp is stale (older than half batch window)
    // This reduces write load while keeping the timestamp reasonably fresh
    val lastUsed = clientToken.lastUsedAt
    val updateThreshold = batchGraceSeconds / 2
    val shouldUpdate = lastUsed == null || lastUsed.plusSeconds(updateThreshold).isBefore(now)

    if (shouldUpdate) {
      clientToken.lastUsedAt = now
      repository.save(clientToken)
      batchTimestampCache.put(batchCacheKey(userId, client), now)
    }
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
    var token = client?.let { lookupToken(user.getOgiriUserId(), it) }
    val isRotation = token != null && token.id != 0L && tokenType == OgiriTokenType.APP

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
    }

    // Rotation bookkeeping: preserve previous token for batch grace period
    if (isRotation) {
      token.previousToken = token.lastToken
      token.lastToken = token.token
    }

    token.expiryAt = expiry
    token.token = hashedToken
    token.tokenUpdatedAt = Instant.now()
    token.plainToken = generatedToken

    val savedToken = repository.save(token)
    evictFromCache(user.getOgiriUserId(), savedToken.client)
    batchTimestampCache.invalidate(batchCacheKey(user.getOgiriUserId(), savedToken.client))

    if (isRotation) {
      auditHook.onTokenRotated(user.getOgiriUserId(), savedToken.client)
    }

    return savedToken
  }

  /**
   * Creates a new APP token for [user]/[client], issues all default sub-tokens, and conditionally
   * cleans up old tokens when approaching the [maxClients] limit.
   *
   * Lower-level than [createNewAuthToken]. Prefer [createNewAuthToken] for the full login flow,
   * which also handles rate limiting and produces a ready-to-send [AuthHeader].
   *
   * @param client Optional client ID; a random ID is generated when `null`.
   * @return The created APP token and a map of sub-tokens keyed by registration name.
   */
  @Transactional
  fun createToken(
      user: OgiriUser,
      client: String?,
  ): OgiriGeneratedTokens<T> {
    val expiry = Instant.now().plus(tokenLifespanDays, ChronoUnit.DAYS)
    val appToken = createOrUpdateToken(user, client, expiry)
    val subTokens = issueSubTokens(user, appToken.client, null, forceNew = false)
    maybeCleanOldTokens(user)
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
        cachedRegistrations.filter {
          requestedNames?.let { names -> it.name in names } ?: it.includeByDefault
        }
    val results = mutableMapOf<String, T>()
    val parentToken = lookupToken(user.getOgiriUserId(), parentClient)
    val parentExpiry =
        parentToken?.expiryAt ?: Instant.now().plus(tokenLifespanDays, ChronoUnit.DAYS)
    registrations.forEach { reg ->
      val subClient = clientForSubToken(reg, parentClient)
      val expiry = reg.expiry(parentExpiry)
      val existing =
          if (!forceNew && !reg.forceNew) lookupToken(user.getOgiriUserId(), subClient) else null
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

      if (existing == null) {
        auditHook.onSubTokenCreated(user.getOgiriUserId(), parentClient, reg.name)
      }
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
      request: HttpServletRequest? =
          (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request,
  ): AuthHeader {
    request?.let { rateLimitHook.beforeTokenCreation(it, userId) }
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
    val tokens = repository.findByUserIdOrderByUpdatedAtDesc(user.getOgiriUserId())
    if (tokens.isEmpty()) return

    val (appTokens, subTokens) =
        tokens.partition { OgiriTokenType.of(it.tokenType) == OgiriTokenType.APP }

    cleanOrphanedSubTokens(subTokens, appTokens)
    enforceMaxClientsLimit(user, appTokens)
  }

  private fun cleanOrphanedSubTokens(subTokens: List<T>, appTokens: List<T>) {
    val expectedSubClients = expectedSubClientsFor(appTokens.clientIds(), cachedRegistrations)
    subTokens.filterOutClientIds(expectedSubClients).forEach { repository.delete(it) }
  }

  private fun enforceMaxClientsLimit(user: OgiriUser, appTokens: List<T>) {
    if (appTokens.size <= maxClients) return

    val toRemoveCount = (appTokens.size - maxClients).toInt()
    val sorted =
        appTokens.sortedWith(
            compareBy<OgiriToken> { it.lastUsedAt ?: it.updatedAt }.thenBy { it.updatedAt },
        )
    val remove = sorted.take(toRemoveCount)
    remove.forEach { repository.delete(it) }
    val removeSubClients = expectedSubClientsFor(remove.clientIds(), cachedRegistrations)
    deleteToken(user.getOgiriUserId(), removeSubClients)
  }

  /**
   * Conditionally clean old tokens only when approaching the max clients limit.
   *
   * This optimization reduces database operations by skipping cleanup when the user has
   * significantly fewer tokens than the maximum allowed. Cleanup is only triggered when the token
   * count reaches 80% of [maxClients].
   *
   * @param user User whose tokens should potentially be cleaned
   */
  @Transactional
  fun maybeCleanOldTokens(user: OgiriUser) {
    val tokenCount = repository.countByUserId(user.getOgiriUserId())
    val threshold = (maxClients * CLEANUP_THRESHOLD_RATIO).toLong()
    if (tokenCount >= threshold) {
      cleanOldTokens(user)
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
    val token = lookupToken(user.getOgiriUserId(), client) ?: return false
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
    return tokensMatch(hashedToken, plainToken)
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

  /**
   * Builds an [AuthHeader] for [user]/[client] without triggering token cleanup or rotation.
   *
   * Batch-fetches sub-token entries to avoid N+1 queries. When [issuedSubTokens] is provided, those
   * plain-token values take precedence over persisted values in the response.
   *
   * @param token Plain (unhashed) access-token value to embed in the header.
   * @param issuedSubTokens Newly issued sub-tokens whose plain values should be returned. Pass
   *   `null` to use only persisted sub-tokens.
   * @see updateAuthHeader for the variant that also runs [maybeCleanOldTokens].
   */
  @Transactional(readOnly = true)
  fun buildAuthHeader(
      user: OgiriUser,
      token: String,
      client: String,
      issuedSubTokens: Map<String, T>? = null,
  ): AuthHeader {
    val appToken = lookupToken(user.getOgiriUserId(), client)

    // Batch fetch all sub-tokens to avoid N+1 queries
    val subClients = cachedRegistrations.map { it.clientIdFor(client) }
    val storedTokens =
        repository.findByUserIdAndClientIn(user.getOgiriUserId(), subClients).associateBy {
          it.client
        }

    val subHeaders = mutableMapOf<String, SubTokenHeader>()
    cachedRegistrations.forEach { reg ->
      val subClient = reg.clientIdFor(client)
      val provided = issuedSubTokens?.get(reg.name)
      val stored = storedTokens[subClient]
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

  /**
   * Builds a refreshed [AuthHeader] and conditionally runs [maybeCleanOldTokens].
   *
   * Equivalent to [buildAuthHeader] plus a cleanup pass. Use [buildAuthHeader] directly when the
   * caller manages cleanup separately.
   *
   * @param token Plain (unhashed) access-token value.
   */
  @Transactional
  fun updateAuthHeader(
      user: OgiriUser,
      token: String,
      client: String,
      issuedSubTokens: Map<String, T>? = null,
  ): AuthHeader {
    val authHeaders = buildAuthHeader(user, token, client, issuedSubTokens)
    maybeCleanOldTokens(user)
    return authHeaders
  }

  /**
   * Returns `true` if the token for [user]/[client] should be rotated.
   *
   * Rotation is triggered when the token is missing or when [OgiriToken.tokenUpdatedAt] is older
   * than [thresholdSeconds] ago. Passing `thresholdSeconds <= 0` always returns `true`
   * (unconditional rotation).
   */
  @Transactional(readOnly = true)
  fun shouldRotate(
      user: OgiriUser,
      client: String,
      thresholdSeconds: Long,
  ): Boolean {
    if (thresholdSeconds <= 0) return true
    val token = lookupToken(user.getOgiriUserId(), client) ?: return true
    val cutoff = Instant.now().minusSeconds(thresholdSeconds)
    return token.tokenUpdatedAt.isBefore(cutoff)
  }

  /**
   * Authenticates a user by email/password and issues a new APP token.
   *
   * On success:
   * 1. Validates the password via BCrypt (constant-time comparison to prevent timing enumeration).
   * 2. Sets the Spring [org.springframework.security.core.context.SecurityContext].
   * 3. Creates a new APP token and appends auth headers to [response].
   * 4. Calls [OgiriUserDirectory.recordSuccessfulLogin] and [OgiriAuditHook.onLoginSuccess].
   *
   * @throws [com.quantipixels.ogiri.security.core.SecurityServiceException] with code
   *   `"error.auth.invalid_credentials"` for unknown users or wrong passwords.
   */
  @Transactional
  fun verifyUser(
      request: HttpServletRequest,
      response: HttpServletResponse,
      email: String,
      password: String,
  ) {
    rateLimitHook.beforeLogin(request, email)

    val user = userDirectory.findByEmail(email)
    val clientIp = request.remoteAddr

    if (user == null) {
      // Constant-time dummy comparison to prevent timing enumeration
      passwordEncoder.matches(password, DUMMY_HASH)
      auditHook.onLoginFailure(email, "user_not_found", clientIp)
      throw SecurityServiceException("error.auth.invalid_credentials")
    }
    if (!passwordEncoder.matches(password, user.password)) {
      auditHook.onLoginFailure(email, "invalid_password", clientIp)
      throw SecurityServiceException("error.auth.invalid_credentials")
    }

    val authentication =
        UsernamePasswordAuthenticationToken(user, "[PROTECTED_PASSWORD]", user.authorities)
    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
    SecurityContextHolder.getContext().authentication = authentication

    val authHeaders = createNewAuthToken(user.getOgiriUserId(), null, request)
    response.appendAuthHeaders(authHeaders, properties.cookies)
    userDirectory.recordSuccessfulLogin(user.getOgiriUserId())
    auditHook.onLoginSuccess(user.getOgiriUserId(), authHeaders.client!!, clientIp)
  }

  companion object {
    private val logger = LoggerFactory.getLogger(OgiriTokenService::class.java)

    // Pre-computed BCrypt hash for timing normalization
    private const val DUMMY_HASH = "\$2a\$10\$dummyhashvalueforconstanttimecheck"
  }

  /**
   * Revokes the token for the client identified in the request's auth headers.
   *
   * Validates the access token before deleting. Silent no-op when the user is not found, the client
   * header is absent, or the token does not match. On successful revocation cascades to all
   * associated sub-tokens and appends (now-invalid) auth headers to [response].
   */
  @Transactional
  fun revokeClient(
      userId: Long,
      request: HttpServletRequest,
      response: HttpServletResponse,
  ) {
    val user = userDirectory.findById(userId) ?: return
    val authHeader = request.extractAuthHeader()
    val clientId = authHeader.client ?: return
    val token = lookupToken(user.getOgiriUserId(), clientId)
    val isTokenValid = token?.let { doesTokenMatch(it.token, authHeader.accessToken) } ?: false

    if (isTokenValid) {
      deleteToken(user.getOgiriUserId(), clientId)
      val subClients =
          cachedRegistrations.map { registration -> registration.clientIdFor(clientId) }
      deleteToken(user.getOgiriUserId(), subClients)
      val authHeaders = buildAuthHeader(user, authHeader.accessToken!!, clientId, null)
      response.appendAuthHeaders(authHeaders, properties.cookies)
      auditHook.onTokenRevoked(user.getOgiriUserId(), clientId)
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
    rateLimitHook.beforeSubTokenRenewal(request, userId)
    val currentUser =
        userDirectory.findById(userId) ?: throw SecurityServiceException("user.not_found")
    val authHeader = request.extractAuthHeader()

    val clientId = authHeader.client ?: throw SecurityServiceException("error.auth.missing_token")
    val parent =
        lookupToken(currentUser.getOgiriUserId(), clientId)
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
    response.appendAuthHeaders(authHeaders, properties.cookies)
  }

  /**
   * Attempts to decode a Base64-encoded JSON sub-bearer fragment into a [SubTokenHeader].
   *
   * [encodedPart] must be the raw Base64 segment — without a `Bearer ` prefix. Returns `null` on
   * Base64 decoding failure, JSON parse error, or type cast error. All other exceptions are
   * re-thrown.
   */
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
    } catch (e: Exception) {
      when (e) {
        is IllegalArgumentException,
        is JsonProcessingException,
        is ClassCastException -> {
          logger.trace(
              "Sub-bearer decode failed (input length={}): {}", encodedPart.length, e.message)
          null
        }
        else -> throw e
      }
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
    val registration = cachedRegistrations.find { it.name == subTokenName } ?: return false
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
        repository
            .findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(user.getOgiriUserId(), subTokenName)
            .filter { OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB }
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
    val registration = cachedRegistrations.find { it.name == subtype } ?: return null

    return repository
        .findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)
        .firstOrNull { token -> OgiriTokenType.of(token.tokenType) == OgiriTokenType.SUB }
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
        repository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtypeName).filter {
          OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB
        }

    if (tokens.isEmpty()) return false

    tokens.forEach { repository.delete(it) }
    auditHook.onSubTokenRevoked(userId, subtypeName)
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
    val parentToken = lookupToken(userId, parentClient) ?: return null
    if (parentToken.isExpired()) return null

    val registration = cachedRegistrations.find { it.name == subtypeName } ?: return null
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
}
