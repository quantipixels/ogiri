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
import org.springframework.stereotype.Repository;

/**
 * Sample token repository adapter using ogiri-jpa module.
 *
 * <p>This demonstrates the simplified approach using AbstractJpaTokenRepositoryAdapter from
 * ogiri-jpa. The adapter reduces boilerplate by providing standard CRUD implementations and
 * requiring only delegation to custom JPA query methods.
 *
 * <p>Implementation steps:
 * <ol>
 *   <li>Extend AbstractJpaTokenRepositoryAdapter&lt;SampleToken, SampleTokenRepository&gt;
 *   <li>Implement abstract methods by delegating to the JPA repository
 * </ol>
 *
 * <p>Benefits:
 * <ul>
 *   <li>Reduces implementation from ~100 lines to ~20 lines
 *   <li>Type-safe delegation to JPA repository methods
 *   <li>Maintains clear separation between OgiriTokenRepository contract and JPA implementation
 *   <li>Standard CRUD operations (save, findById, delete) provided by base class
 * </ul>
 *
 * <p>Before (manual implementation): ~100 lines with all method implementations. After (using
 * adapter): ~20 lines with just delegation methods
 */
@Repository
public class SampleTokenRepositoryAdapter
    extends AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenRepository> {

  public SampleTokenRepositoryAdapter(SampleTokenRepository jpaRepository) {
    super(jpaRepository);
  }

  @Override
  public List<SampleToken> findByUserIdOrderByUpdatedAtDesc(long userId) {
    return jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId);
  }

  @Override
  public SampleToken findByUserIdAndClientEquals(long userId, String client) {
    return jpaRepository.findByUserIdAndClientEquals(userId, client).orElse(null);
  }

  @Override
  public List<SampleToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      long userId, String subtype) {
    return jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype);
  }

  @Override
  public List<SampleToken> findByExpiryAtBeforeCutoff(Instant cutoff) {
    return jpaRepository.findByExpiryAtBeforeCutoff(cutoff);
  }

  @Override
  public void deleteByUserIdAndClientEquals(long userId, String client) {
    jpaRepository.deleteByUserIdAndClientEquals(userId, client);
  }

  @Override
  public void deleteByUserIdAndClientIdIn(long userId, Collection<String> clientIds) {
    jpaRepository.deleteByUserIdAndClientIdIn(userId, clientIds);
  }

  @Override
  public void deleteByUserIdJpa(long userId) {
    jpaRepository.deleteByUserIdJpa(userId);
  }
}
