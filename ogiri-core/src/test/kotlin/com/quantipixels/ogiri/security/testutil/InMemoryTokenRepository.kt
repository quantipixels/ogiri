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
package com.quantipixels.ogiri.security.testutil

import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory TokenRepository implementation for testing.
 *
 * This repository stores all tokens in a mutable list, simulating a database without requiring
 * actual database connections. It's useful for unit testing TokenService and other components that
 * depend on TokenRepository.
 *
 * Thread-safe operations are supported through synchronized blocks.
 */
class InMemoryTokenRepository : OgiriTokenRepository<TestToken> {
  private val tokens = mutableListOf<TestToken>()
  private val idSequence = AtomicLong(1L)
  private var clock: Instant = Instant.now()

  /** Removes all tokens from the repository in a thread-safe manner. */
  fun clear() {
    synchronized(tokens) { tokens.clear() }
  }

  /**
   * Provide a snapshot of all tokens stored in the repository.
   *
   * @return A list containing a copy of all stored TestToken entries.
   */
  fun getAllTokens(): List<TestToken> {
    synchronized(tokens) {
      return tokens.toList()
    }
  }

  /**
   * Retrieves the number of tokens currently stored in the repository.
   *
   * @return The number of tokens currently stored in the repository.
   */
  fun getCount(): Int {
    synchronized(tokens) {
      return tokens.size
    }
  }

  /**
   * Advance the repository's internal clock by one second to simulate time progression in tests.
   */
  fun incrementClock() {
    synchronized(tokens) { clock = clock.plusSeconds(1) }
  }

  /**
   * Insert or update a token in the repository.
   *
   * When the token's id is 0 a new id is assigned, the token's transient `plainToken` is preserved,
   * and `updatedAt` is set to the repository clock; otherwise the existing token with the same id
   * is replaced and its `updatedAt` is set to the repository clock.
   *
   * @param token The token to save or update.
   * @return The saved token with an assigned id for inserts and an updated `updatedAt` timestamp.
   */
  @Suppress("UNCHECKED_CAST")
  override fun <S : TestToken> save(token: S): S {
    synchronized(tokens) {
      return if (token.id == 0L) {
        // Insert: generate new ID and preserve transient properties
        val newToken = token.copy(id = idSequence.getAndIncrement())
        newToken.plainToken = token.plainToken // Preserve transient property
        newToken.updatedAt = clock // Use repository's clock for deterministic testing
        tokens.add(newToken)
        newToken as S
      } else {
        // Update: remove old, add new
        tokens.removeIf { it.id == token.id }
        token.updatedAt = clock // Use repository's clock for deterministic testing
        tokens.add(token)
        token
      }
    }
  }

  /**
   * Finds the token with the given ID.
   *
   * @param id The token ID to look up.
   * @return Optional containing the token if found, empty otherwise.
   */
  override fun findById(id: Long): Optional<TestToken> {
    synchronized(tokens) {
      return Optional.ofNullable(tokens.find { it.id == id })
    }
  }

  /**
   * Removes the token with the given id from the repository if present.
   *
   * @param id The token id to delete.
   */
  override fun deleteById(id: Long) {
    synchronized(tokens) { tokens.removeIf { it.id == id } }
  }

  /**
   * Retrieve all tokens belonging to the specified user, ordered by updatedAt descending.
   *
   * @return A list of TestToken objects associated with the provided user ID.
   */
  override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId }.sortedByDescending { it.updatedAt }
    }
  }

  /**
   * Finds all tokens for the specified user that have the given token subtype, ordered by updatedAt
   * descending.
   *
   * @param userId The id of the user whose tokens to search.
   * @param tokenSubtype The token subtype to match.
   * @return A list of TestToken instances matching the given `userId` and `tokenSubtype`.
   */
  override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<TestToken> {
    synchronized(tokens) {
      return tokens
          .filter { it.userId == userId && it.tokenSubtype == tokenSubtype }
          .sortedByDescending { it.updatedAt }
    }
  }

  /**
   * Retrieves the token for the specified user and client.
   *
   * @return Optional containing the token if found, empty otherwise.
   */
  override fun findByUserIdAndClient(
      userId: Long,
      client: String,
  ): Optional<TestToken> {
    synchronized(tokens) {
      return Optional.ofNullable(tokens.find { it.userId == userId && it.client == client })
    }
  }

  /**
   * Find tokens for a user matching any of the given clients.
   *
   * Used for batch loading sub-tokens to avoid N+1 queries.
   *
   * @param userId The user's primary key ID.
   * @param clients Collection of client identifiers to match.
   * @return List of tokens matching any of the clients.
   */
  override fun findByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  ): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId && it.client in clients }
    }
  }

  /**
   * Returns all tokens whose `expiryAt` timestamp is before the given cutoff `Instant`.
   *
   * @param cutoff The cutoff instant; tokens with `expiryAt` strictly before this value are
   *   returned.
   * @return A list of tokens that expired before `cutoff`.
   */
  override fun findByExpiryAtBefore(cutoff: Instant): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.expiryAt.isBefore(cutoff) }
    }
  }

  /**
   * Find all tokens of a specific type.
   *
   * @param tokenType The token type to filter by
   * @return List of tokens matching the type
   */
  override fun findByTokenType(tokenType: String): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.tokenType.equals(tokenType, ignoreCase = true) }
    }
  }

  /**
   * Deletes all tokens that belong to the specified user and client.
   *
   * @param userId The user's ID whose tokens should be deleted.
   * @param client The client identifier to match when deleting tokens.
   */
  override fun deleteByUserIdAndClient(
      userId: Long,
      client: String,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client == client } }
  }

  /**
   * Remove all tokens that belong to the given user and whose client is in the provided collection.
   *
   * @param userId The user's ID whose tokens should be removed.
   * @param clients The collection of client identifiers; any token whose client is contained in
   *   this collection will be deleted.
   */
  override fun deleteByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client in clients } }
  }

  /** Delete all tokens for a user. */
  override fun deleteByUserId(userId: Long) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId } }
  }

  /**
   * Delete the given token.
   *
   * @param token The token to delete.
   */
  override fun delete(token: TestToken) {
    synchronized(tokens) { tokens.removeIf { it.id == token.id } }
  }

  /**
   * Count the number of tokens for a user.
   *
   * @param userId The user ID to count tokens for
   * @return Number of tokens belonging to the user
   */
  override fun countByUserId(userId: Long): Long {
    synchronized(tokens) {
      return tokens.count { it.userId == userId }.toLong()
    }
  }

  /**
   * Delete all tokens that expired before the cutoff.
   *
   * @param cutoff Tokens with expiryAt before this are deleted.
   * @return Number of tokens deleted.
   */
  override fun deleteByExpiryAtBefore(cutoff: Instant): Int {
    synchronized(tokens) {
      val expired = tokens.filter { it.expiryAt.isBefore(cutoff) }
      tokens.removeIf { it.expiryAt.isBefore(cutoff) }
      return expired.size
    }
  }
}
