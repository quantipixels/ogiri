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
package com.quantipixels.ogiri.samples.java.entity;

import com.quantipixels.ogiri.jpa.OgiriBaseTokenEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Sample JPA Token entity extending OgiriBaseTokenEntity.
 *
 * <p>By extending OgiriBaseTokenEntity, all 15+ token fields with proper JPA annotations are
 * inherited automatically:
 *
 * <ul>
 *   <li>id (auto-generated)
 *   <li>userId, client, token, tokenType
 *   <li>expiryAt, tokenUpdatedAt, createdAt, updatedAt
 *   <li>previousToken, lastToken, tokenPrefix, tokenSubtype
 *   <li>lastUsedAt
 *   <li>plainToken (transient, not persisted)
 * </ul>
 *
 * <p>Users only need to add @Entity, @Table with indexes and constraints.
 */
@Entity
@Table(
    name = "user_tokens",
    indexes = {
      @Index(name = "idx_user_tokens_user_id", columnList = "user_id"),
      @Index(name = "idx_user_tokens_expiry", columnList = "expiry_at"),
    },
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_tokens_user_client", columnNames = {"user_id", "client"}),
    })
public class SampleToken extends OgiriBaseTokenEntity {

  /** Default constructor for JPA. */
  public SampleToken() {
    super();
  }
}
