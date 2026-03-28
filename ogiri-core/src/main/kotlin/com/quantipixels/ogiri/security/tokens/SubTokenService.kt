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
import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.JsonCodec
import com.quantipixels.ogiri.security.core.SubTokenHeader
import com.quantipixels.ogiri.security.spi.NoOpOgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriAuditHook
import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/**
 * Handles sub-bearer token creation, validation, and registry-driven lifecycle operations.
 *
 * This class is internal to the tokens package. It is instantiated and owned by
 * [OgiriTokenService], which delegates all sub-token concerns here.
 *
 * The functional callbacks — [lookupToken], [createOrUpdateToken], [deleteToken], [doesTokenMatch]
 * — are injected by [OgiriTokenService] after construction to avoid a circular constructor
 * dependency.
 */
internal class SubTokenService<T : OgiriToken>(
    private val subTokenRegistry: OgiriSubTokenRegistry,
    private val repository: OgiriTokenRepository<T>,
    private val userDirectory: OgiriUserDirectory,
) {
  private var auditHook: OgiriAuditHook = NoOpOgiriAuditHook

  internal fun setAuditHook(hook: OgiriAuditHook) {
    this.auditHook = hook
  }

  // Callbacks wired by OgiriTokenService after construction
  internal lateinit var lookupToken: (userId: Long, client: String) -> T?
  internal lateinit var createOrUpdateToken:
      (
          user: OgiriUser,
          client: String?,
          expiry: Instant,
          tokenType: OgiriTokenType,
          tokenSubtype: String?) -> T
  internal lateinit var deleteToken: (userId: Long, clients: Collection<String>) -> Unit
  internal lateinit var doesTokenMatch: (hashedToken: String?, plainToken: String?) -> Boolean

  internal val cachedRegistrations: List<OgiriSubTokenRegistration> by lazy {
    subTokenRegistry.registrations()
  }

  internal fun clientForSubToken(
      reg: OgiriSubTokenRegistration,
      parentClient: String,
  ): String = reg.clientIdFor(parentClient)

  @Transactional
  internal fun issueSubTokens(
      user: OgiriUser,
      parentClient: String,
      requestedNames: Collection<String>?,
      forceNew: Boolean,
      tokenLifespanDays: Long,
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
                  user,
                  subClient,
                  expiry,
                  OgiriTokenType.SUB,
                  reg.name,
              )
      results[reg.name] = token

      if (existing == null) {
        auditHook.onSubTokenCreated(user.getOgiriUserId(), parentClient, reg.name)
      }
    }
    return results
  }

  /**
   * Attempts to decode a Base64-encoded JSON sub-bearer fragment into a [SubTokenHeader].
   *
   * [encodedPart] must be the raw Base64 segment — without a `Bearer ` prefix. Returns `null` on
   * Base64 decoding failure, JSON parse error, or type cast error. All other exceptions are
   * re-thrown.
   */
  internal fun tryDecodeSubBearer(encodedPart: String): SubTokenHeader? =
      try {
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
  internal fun validateSubToken(
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
          repository
              .findByUserIdAndClient(user.getOgiriUserId(), authHeader.client)
              .orElse(null)
              ?.takeIf {
                OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB &&
                    it.tokenSubtype == subTokenName
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
  internal fun getSubToken(
      userId: Long,
      subtype: String,
  ): OgiriToken? {
    if (cachedRegistrations.none { it.name == subtype }) return null

    return repository
        .findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)
        .firstOrNull { token -> OgiriTokenType.of(token.tokenType) == OgiriTokenType.SUB }
  }

  /**
   * Revoke all sub-tokens of a specific type for a user.
   *
   * @param userId User ID
   * @param subtypeName Sub-token type to revoke (e.g., "device", "chat")
   * @return true if at least one token was revoked, false if none existed
   */
  @Transactional
  internal fun revokeSubToken(
      userId: Long,
      subtypeName: String,
  ): Boolean {
    val tokens =
        repository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtypeName).filter {
          OgiriTokenType.of(it.tokenType) == OgiriTokenType.SUB
        }

    if (tokens.isEmpty()) return false

    deleteToken(userId, tokens.map { it.client })
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
  internal fun renewSubToken(
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
            user,
            subClient,
            registration.expiry(parentToken.expiryAt),
            OgiriTokenType.SUB,
            subtypeName,
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
  internal fun expectedSubClientsFor(
      appClients: Set<String>,
      registrations: List<OgiriSubTokenRegistration>,
  ): Set<String> =
      appClients.flatMap { parent -> registrations.map { it.clientIdFor(parent) } }.toSet()

  companion object {
    private val logger = LoggerFactory.getLogger(SubTokenService::class.java)
  }
}
