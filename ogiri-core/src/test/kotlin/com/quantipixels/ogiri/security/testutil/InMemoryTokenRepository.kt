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

import com.quantipixels.ogiri.security.tokens.TokenRepository
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
class InMemoryTokenRepository : TokenRepository<TestToken> {
  private val tokens = mutableListOf<TestToken>()
  private val idSequence = AtomicLong(1L)

  /** Clear all tokens from the repository. Useful for test cleanup. */
  fun clear() {
    synchronized(tokens) { tokens.clear() }
  }

  /** Get all tokens currently in the repository. Useful for assertions in tests. */
  fun getAllTokens(): List<TestToken> {
    synchronized(tokens) {
      return tokens.toList()
    }
  }

  /** Get the count of tokens in the repository. */
  fun getCount(): Int {
    synchronized(tokens) {
      return tokens.size
    }
  }

  /**
   * Save or update a token. If id is 0, generates a new ID and inserts. If id > 0, updates existing
   * token.
   */
  override fun save(token: TestToken): TestToken {
    synchronized(tokens) {
      return if (token.id == 0L) {
        // Insert: generate new ID and preserve transient plainToken
        val newToken = token.copy(id = idSequence.getAndIncrement())
        newToken.plainToken = token.plainToken // Preserve transient property
        tokens.add(newToken)
        newToken
      } else {
        // Update: remove old, add new
        tokens.removeIf { it.id == token.id }
        tokens.add(token)
        token
      }
    }
  }

  /** Find a token by ID. */
  override fun findById(id: Long): TestToken? {
    synchronized(tokens) {
      return tokens.find { it.id == id }
    }
  }

  /** Delete a token by ID. */
  override fun deleteById(id: Long) {
    synchronized(tokens) { tokens.removeIf { it.id == id } }
  }

  /** Find all tokens for a user. */
  override fun findAllByUserId(userId: Long): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId }
    }
  }

  /** Find all tokens for a user with the given subtype. */
  override fun findAllByUserIdAndTokenSubtype(
      userId: Long,
      tokenSubtype: String,
  ): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.userId == userId && it.tokenSubtype == tokenSubtype }
    }
  }

  /** Find the token for a user and client. */
  override fun findByUserIdAndClient(
      userId: Long,
      clientId: String,
  ): TestToken? {
    synchronized(tokens) {
      return tokens.find { it.userId == userId && it.client == clientId }
    }
  }

  /** Find all tokens that have expired before a cutoff time. */
  override fun findByExpiryAtBefore(cutoff: Instant): List<TestToken> {
    synchronized(tokens) {
      return tokens.filter { it.expiryAt.isBefore(cutoff) }
    }
  }

  /** Delete the token for a user and client. */
  override fun deleteByUserIdAndClient(
      userId: Long,
      clientId: String,
  ) {
    synchronized(tokens) { tokens.removeIf { it.userId == userId && it.client == clientId } }
  }

  /** Delete tokens for multiple clients. */
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
