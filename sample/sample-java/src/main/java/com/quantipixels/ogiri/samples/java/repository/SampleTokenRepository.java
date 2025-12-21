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
package com.quantipixels.ogiri.samples.java.repository;

import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA repository interface for SampleToken persistence.
 *
 * <p>Extends Spring Data JPA's JpaRepository to provide standard CRUD operations and custom query
 * methods for token management.
 *
 * <p>All query methods use explicit @Query annotations to avoid Spring Data's method name parsing
 * which can be error-prone with complex property names.
 */
public interface SampleTokenRepository extends JpaRepository<SampleToken, Long> {

  /**
   * Find all tokens for a specific user, ordered by most recent first.
   *
   * @param userId The user ID
   * @return List of tokens ordered by updatedAt DESC, then id DESC for deterministic ordering
   */
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC, t.id DESC")
  List<SampleToken> findByUserIdOrderByUpdatedAtDesc(Long userId);

  /**
   * Find a specific token by user ID and client identifier.
   *
   * @param userId The user ID
   * @param client The client/application identifier
   * @return The token if found, empty Optional otherwise
   */
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  Optional<SampleToken> findByUserIdAndClientEquals(Long userId, String client);

  /** Find all tokens for a user with a specific subtype. */
  @Query(
      "SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 "
          + "ORDER BY t.updatedAt DESC, t.id DESC")
  List<SampleToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      Long userId, String tokenSubtype);

  /**
   * Find all tokens that expired before a specific cutoff time.
   *
   * @param expiryAt The expiry time threshold
   * @return List of expired tokens
   */
  @Query("SELECT t FROM SampleToken t WHERE t.expiryAt < ?1")
  List<SampleToken> findByExpiryAtBeforeCutoff(Instant expiryAt);

  /**
   * Delete a token by user ID and client identifier.
   *
   * @param userId The user ID
   * @param client The client/application identifier
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  void deleteByUserIdAndClientEquals(Long userId, String client);

  /**
   * Delete tokens by user ID and multiple client identifiers.
   *
   * @param userId The user ID
   * @param clients Collection of client identifiers to delete
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  void deleteByUserIdAndClientIdIn(Long userId, Collection<String> clients);

  /**
   * Delete all tokens for a specific user.
   *
   * @param userId The user ID
   */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  void deleteByUserIdJpa(Long userId);

  default List<SampleToken> findAllByUserId(Long userId) {
    return findByUserIdOrderByUpdatedAtDesc(userId);
  }

  default SampleToken findByUserIdAndClient(Long userId, String clientId) {
    return findByUserIdAndClientEquals(userId, clientId).orElse(null);
  }

  default void deleteByUserIdAndClient(Long userId, String clientId) {
    deleteByUserIdAndClientEquals(userId, clientId);
  }

  default void deleteByUserIdAndClientIn(Long userId, Collection<String> clientIds) {
    deleteByUserIdAndClientIdIn(userId, clientIds);
  }

  default void deleteByUserId(Long userId) {
    deleteByUserIdJpa(userId);
  }

  default List<SampleToken> findAllByUserIdAndTokenSubtype(Long userId, String tokenSubtype) {
    return findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype);
  }

  default List<SampleToken> findByExpiryAtBefore(Instant cutoff) {
    return findByExpiryAtBeforeCutoff(cutoff);
  }
}
