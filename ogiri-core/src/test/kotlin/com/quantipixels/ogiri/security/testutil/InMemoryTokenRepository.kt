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

  /**
   * Removes all tokens from the repository in a thread-safe manner.
   */
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
  override fun save(token: TestToken): TestToken {
    synchronized(tokens) {
      return if (token.id == 0L) {
        // Insert: generate new ID and preserve transient plainToken
        val newToken = token.copy(id = idSequence.getAndIncrement())
        newToken.plainToken = token.plainToken // Preserve transient property
        newToken.updatedAt = clock // Use repository's clock for deterministic testing
        tokens.add(newToken)
        newToken
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
   * @return The matching TestToken if found, `null` otherwise.
   */
  override fun findById(id: Long): TestToken? {
    synchronized(tokens) {
      return tokens.find { it.id == id }
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
   * Retrieve all tokens belonging to the specified user.
   *
   * @return A list of TestToken objects associated with the provided user ID.
   */
  override fun findAllByUserId(userId: Long): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId }
    }
  }

  /**
   * Finds all tokens for the specified user that have the given token subtype.
   *
   * @param userId The id of the user whose tokens to search.
   * @param tokenSubtype The token subtype to match.
   * @return A list of TestToken instances matching the given `userId` and `tokenSubtype`.
   */
  override fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId && it.tokenSubtype == tokenSubtype }
    }
  }

  /**
   * Retrieves the token for the specified user and client.
   *
   * @return The token matching the given `userId` and `clientId`, or `null` if none exists.
   */
  override fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): TestToken? {
    synchronized(tokens) {
      return tokens.find { it.userId == userId && it.client == clientId }
    }
  }

  /**
   * Returns all tokens whose `expiryAt` timestamp is before the given cutoff `Instant`.
   *
   * @param cutoff The cutoff instant; tokens with `expiryAt` strictly before this value are returned.
   * @return A list of tokens that expired before `cutoff`.
   */
  override fun findByExpiryAtBefore(cutoff: Instant): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.expiryAt.isBefore(cutoff) }
    }
  }

  /**
   * Deletes all tokens that belong to the specified user and client.
   *
   * @param userId The user's ID whose tokens should be deleted.
   * @param clientId The client identifier to match when deleting tokens.
   */
  override fun deleteByUserIdAndClient(
      userId: Long,
      clientId: String,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client == clientId } }
  }

  /**
   * Remove all tokens that belong to the given user and whose client is in the provided collection.
   *
   * @param userId The user's ID whose tokens should be removed.
   * @param clientIds The collection of client identifiers; any token whose client is contained in this collection will be deleted.
   */
  override fun deleteByUserIdAndClientIn(
      userId: Long,
      clientIds: Collection<String>,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client in clientIds } }
  }

  /** Delete all tokens for a user. */
  override fun deleteByUserId(userId: Long) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId } }
  }
}
