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
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * Adapter that implements TokenRepository for SampleTokenRepository.
 *
 * <p>This adapter delegates to SampleTokenRepository (JpaRepository) to provide the TokenRepository
 * interface required by ogiri's TokenService.
 *
 * <p>The adapter pattern is used to avoid method signature conflicts between TokenRepository.save()
 * and JpaRepository.save() which have different generic type bounds.
 */
@Repository
public class SampleTokenRepositoryAdapter implements OgiriTokenRepository<SampleToken> {

  private final SampleTokenRepository jpaRepository;

  public SampleTokenRepositoryAdapter(SampleTokenRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public SampleToken save(SampleToken token) {
    return jpaRepository.save(token);
  }

  public SampleToken findById(long id) {
    return jpaRepository.findById(id).orElse(null);
  }

  public void deleteById(long id) {
    jpaRepository.deleteById(id);
  }

  public void deleteAll(Collection<? extends SampleToken> tokens) {
    jpaRepository.deleteAll(tokens);
  }

  public List<SampleToken> findAllByUserId(long userId) {
    return jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId);
  }

  public SampleToken findByUserIdAndClient(long userId, String clientId) {
    return jpaRepository.findByUserIdAndClientEquals(userId, clientId).orElse(null);
  }

  @Override
  public List<SampleToken> findAllByUserIdAndTokenSubtype(long userId, String tokenSubtype) {
    return jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype);
  }

  public List<SampleToken> findByExpiryAtBefore(Instant cutoff) {
    return jpaRepository.findByExpiryAtBeforeCutoff(cutoff);
  }

  public void deleteByUserIdAndClient(long userId, String clientId) {
    jpaRepository.deleteByUserIdAndClientEquals(userId, clientId);
  }

  public void deleteByUserIdAndClientIn(long userId, Collection<String> clientIds) {
    jpaRepository.deleteByUserIdAndClientIdIn(userId, clientIds);
  }

  public void deleteByUserId(long userId) {
    jpaRepository.deleteByUserIdJpa(userId);
  }

  @Override
  public void delete(SampleToken token) {
    jpaRepository.delete(token);
  }
}
