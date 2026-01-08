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

import com.quantipixels.ogiri.jpa.AbstractJpaTokenRepositoryAdapter;
import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

/**
 * Repository adapter extending AbstractJpaTokenRepositoryAdapter.
 *
 * <p>This adapter delegates to SampleTokenJpaRepository and provides the OgiriTokenRepository
 * interface required by Ogiri's TokenService.
 *
 * <p>By extending AbstractJpaTokenRepositoryAdapter, most of the boilerplate is eliminated. Only
 * the custom JPA query delegations need to be implemented.
 */
@Repository
@Primary
public class SampleTokenRepositoryAdapter
    extends AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenJpaRepository> {

  public SampleTokenRepositoryAdapter(SampleTokenJpaRepository jpaRepository) {
    super(jpaRepository);
  }

  @Override
  @NotNull
  protected List<SampleToken> findByUserIdOrderByUpdatedAtDesc(long userId) {
    return getJpaRepository().findByUserIdOrderByUpdatedAtDesc(userId);
  }

  @Override
  @Nullable
  protected SampleToken findByUserIdAndClientEquals(long userId, @NotNull String client) {
    return getJpaRepository().findByUserIdAndClient(userId, client).orElse(null);
  }

  @Override
  @NotNull
  protected List<SampleToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      long userId, @NotNull String subtype) {
    return getJpaRepository().findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype);
  }

  @Override
  @NotNull
  protected List<SampleToken> findByExpiryAtBeforeCutoff(@NotNull Instant cutoff) {
    return getJpaRepository().findByExpiryAtBefore(cutoff);
  }

  @Override
  protected void deleteByUserIdAndClientEquals(long userId, @NotNull String client) {
    getJpaRepository().deleteByUserIdAndClient(userId, client);
  }

  @Override
  protected void deleteByUserIdAndClientIdIn(
      long userId, @NotNull Collection<String> clientIds) {
    getJpaRepository().deleteByUserIdAndClientIn(userId, clientIds);
  }

  @Override
  protected void deleteByUserIdJpa(long userId) {
    getJpaRepository().deleteByUserId(userId);
  }

  @Override
  public int deleteByExpiryAtBefore(Instant cutoff) {
    return jpaRepository.deleteByExpiryAtBeforeCutoff(cutoff);
  }

  @Override
  public int deleteExpiredBatch(Instant cutoff, int batchSize) {
    // Default implementation: delegate to deleteByExpiryAtBefore
    // For production, you should override with a batched implementation using native queries
    var expired = jpaRepository.findByExpiryAtBeforeCutoff(cutoff);
    var toDelete = expired.stream().limit(batchSize).toList();
    jpaRepository.deleteAll(toDelete);
    return toDelete.size();
  }

  @Override
  public List<SampleToken> findValidTokensByPrefix(String prefix, Instant now) {
    // Default implementation using filtering
    // For production, you should override with an indexed query:
    // @Query("SELECT t FROM SampleToken t WHERE t.tokenPrefix = :prefix AND t.tokenType = 'APP' AND
    // t.expiryAt > :now")
    // List<SampleToken> findValidByPrefix(@Param("prefix") String prefix, @Param("now") Instant
    // now);
    return jpaRepository.findAll().stream()
        .filter(t -> prefix.equals(t.getTokenPrefix()))
        .filter(t -> "app".equals(t.getTokenType()))
        .filter(t -> t.getExpiryAt().isAfter(now))
        .toList();
  }

  @Override
  public List<SampleToken> findAllByTokenType(String tokenType) {
    // Default implementation using filtering
    // For production, you should override with a native query:
    // @Query("SELECT t FROM SampleToken t WHERE t.tokenType = :tokenType")
    // List<SampleToken> findByTokenType(@Param("tokenType") String tokenType);
    return jpaRepository.findAll().stream()
        .filter(t -> tokenType.equals(t.getTokenType()))
        .toList();
  }

  @Override
  public long countByUserId(long userId) {
    // For production, you should override with a COUNT query:
    // @Query("SELECT COUNT(t) FROM SampleToken t WHERE t.userId = :userId")
    // long countByUserId(@Param("userId") long userId);
    return jpaRepository.countByUserId(userId);
  }
}
