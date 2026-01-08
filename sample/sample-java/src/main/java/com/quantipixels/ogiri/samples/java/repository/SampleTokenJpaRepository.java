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
 * Pure JPA repository interface for SampleToken persistence.
 *
 * <p>Contains only the JPA-specific query methods. The adapter class (SampleTokenRepositoryAdapter)
 * extends AbstractJpaTokenRepositoryAdapter to implement OgiriTokenRepository using this
 * repository.
 */
public interface SampleTokenJpaRepository extends JpaRepository<SampleToken, Long> {

  /** Find all tokens for a user, ordered by updated_at DESC. */
  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC, t.id DESC")
  List<SampleToken> findByUserIdOrderByUpdatedAtDesc(Long userId);

  /** Find a specific token by user ID and client identifier. */
  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  Optional<SampleToken> findByUserIdAndClient(Long userId, String client);

  /** Find all tokens for a user with a specific subtype. */
  @Transactional(readOnly = true)
  @Query(
      "SELECT t FROM SampleToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 "
          + "ORDER BY t.updatedAt DESC, t.id DESC")
  List<SampleToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      Long userId, String tokenSubtype);

  /** Find all tokens that expired before a specific cutoff time. */
  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.expiryAt < ?1")
  List<SampleToken> findByExpiryAtBefore(Instant expiryAt);

  /** Count tokens by user ID. */
  @Transactional(readOnly = true)
  @Query("SELECT COUNT(t) FROM SampleToken t WHERE t.userId = ?1")
  long countByUserId(Long userId);

  /** Find valid tokens by prefix. */
  @Transactional(readOnly = true)
  @Query(
      "SELECT t FROM SampleToken t WHERE t.tokenPrefix = ?1 AND t.tokenType = 'APP' AND t.expiryAt > ?2")
  List<SampleToken> findValidByPrefix(String prefix, Instant now);

  /** Find all tokens by token type. */
  @Transactional(readOnly = true)
  @Query("SELECT t FROM SampleToken t WHERE t.tokenType = ?1")
  List<SampleToken> findByTokenType(String tokenType);

  /** Delete a token by user ID and client identifier. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  void deleteByUserIdAndClient(Long userId, String client);

  /** Delete tokens by user ID and multiple client identifiers. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  void deleteByUserIdAndClientIn(Long userId, Collection<String> clients);

  /** Delete all tokens for a specific user. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  void deleteByUserId(Long userId);

  /** Delete expired tokens and return count. */
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.expiryAt < ?1")
  int deleteByExpiryAtBefore(Instant cutoff);
}
