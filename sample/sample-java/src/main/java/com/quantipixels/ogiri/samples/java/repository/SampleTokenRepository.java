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
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for SampleToken using the simplified pattern.
 *
 * <p>Since 1.3.1, OgiriTokenRepository method names follow Spring Data conventions, so Spring Data
 * automatically generates most query implementations. No adapter class needed!
 *
 * <p>For methods that Spring Data cannot auto-generate (bulk deletes), we provide explicit @Query
 * annotations.
 */
@Repository
public interface SampleTokenRepository
    extends JpaRepository<SampleToken, Long>, OgiriTokenRepository<SampleToken> {

  // Explicit override to resolve method ambiguity between JpaRepository and OgiriTokenRepository
  // These are automatically implemented by Spring Data JPA
  @Override
  <S extends SampleToken> S save(S entity);

  @Override
  void delete(SampleToken entity);

  @Override
  void deleteById(Long id);

  // Spring Data auto-generates these based on method naming conventions:
  // - findByUserIdOrderByUpdatedAtDesc(userId)
  // - findByUserIdAndClient(userId, client) -> Optional<SampleToken>
  // - findByUserIdAndClientIn(userId, clients) -> List<SampleToken> (for batch sub-token loading)
  // - findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)
  // - findByExpiryAtBefore(cutoff)
  // - findByTokenType(tokenType)

  // Bulk delete operations need explicit @Query (Spring Data naming convention doesn't support IN
  // clause)
  @Override
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
  void deleteByUserIdAndClient(long userId, String client);

  @Override
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
  void deleteByUserIdAndClientIn(long userId, Collection<String> clients);

  @Override
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
  void deleteByUserId(long userId);

  // Optional performance override for bulk delete
  @Transactional
  @Modifying
  @Query("DELETE FROM SampleToken t WHERE t.expiryAt < ?1")
  int deleteByExpiryAtBefore(Instant cutoff);

  // Optional performance override for count (uses COUNT instead of loading all)
  @Override
  @Transactional(readOnly = true)
  @Query("SELECT COUNT(t) FROM SampleToken t WHERE t.userId = ?1")
  long countByUserId(long userId);
}
