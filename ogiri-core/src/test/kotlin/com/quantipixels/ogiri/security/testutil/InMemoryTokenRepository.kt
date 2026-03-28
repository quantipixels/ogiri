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

  fun getAllTokens(): List<TestToken> {
    synchronized(tokens) {
      return tokens.toList()
    }
  }

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
   * Inserts or updates a token. On insert, assigns a new id and preserves `plainToken`. On update,
   * replaces the existing record. In both cases sets `updatedAt` to the repository clock for
   * deterministic test time control.
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

  override fun findById(id: Long): Optional<TestToken> {
    synchronized(tokens) {
      return Optional.ofNullable(tokens.find { it.id == id })
    }
  }

  override fun deleteById(id: Long) {
    synchronized(tokens) { tokens.removeIf { it.id == id } }
  }

  override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId }.sortedByDescending { it.updatedAt }
    }
  }

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

  override fun findByUserIdAndClient(
      userId: Long,
      client: String,
  ): Optional<TestToken> {
    synchronized(tokens) {
      return Optional.ofNullable(tokens.find { it.userId == userId && it.client == client })
    }
  }

  override fun findByUserIdAndClientIn(
      userId: Long,
      clients: Collection<String>,
  ): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId && it.client in clients }
    }
  }

  override fun findByExpiryAtBefore(cutoff: Instant): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.expiryAt.isBefore(cutoff) }
    }
  }

  override fun findByTokenType(tokenType: String): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.tokenType.equals(tokenType, ignoreCase = true) }
    }
  }

  override fun deleteByUserIdAndClient(
      userId: Long,
      client: String,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client == client } }
  }

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

  override fun delete(token: TestToken) {
    synchronized(tokens) { tokens.removeIf { it.id == token.id } }
  }

  override fun countByUserId(userId: Long): Long {
    synchronized(tokens) {
      return tokens.count { it.userId == userId }.toLong()
    }
  }

  override fun deleteByExpiryAtBefore(cutoff: Instant): Int {
    synchronized(tokens) {
      val count = tokens.count { it.expiryAt.isBefore(cutoff) }
      tokens.removeIf { it.expiryAt.isBefore(cutoff) }
      return count
    }
  }
}
