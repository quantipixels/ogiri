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
package com.quantipixels.ogiri.samples.java.jdbc;

import com.quantipixels.ogiri.jdbc.OgiriJdbcTokenRepository;
import java.sql.Timestamp;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * JDBC repository for JdbcSampleToken.
 *
 * <p>Extend {@link OgiriJdbcTokenRepository} and provide:
 *
 * <ul>
 *   <li>{@link #tableName()}: the target table (must match your schema)
 *   <li>{@link #rowMapper()}: maps a ResultSet row to your token class
 * </ul>
 *
 * <p>All 15 OgiriTokenRepository methods are handled by the base class via JdbcClient.
 */
@Repository
@Profile("jdbc")
public class JdbcSampleTokenRepository extends OgiriJdbcTokenRepository<JdbcSampleToken> {

  public JdbcSampleTokenRepository(JdbcClient jdbcClient) {
    super(jdbcClient);
  }

  @Override
  public String tableName() {
    return "user_tokens";
  }

  @Override
  public RowMapper<JdbcSampleToken> rowMapper() {
    return (rs, rowNum) -> {
      JdbcSampleToken token = new JdbcSampleToken();
      token.setId(rs.getLong("id"));
      token.setUserId(rs.getLong("user_id"));
      token.setClient(rs.getString("client"));
      token.setToken(rs.getString("token_hash"));
      token.setTokenType(rs.getString("token_type"));
      token.setTokenSubtype(rs.getString("token_subtype"));
      token.setExpiryAt(rs.getTimestamp("expiry_at").toInstant());
      token.setPreviousToken(rs.getString("previous_token_hash"));
      token.setLastToken(rs.getString("last_token_hash"));
      token.setTokenUpdatedAt(rs.getTimestamp("token_updated_at").toInstant());
      Timestamp lastUsedAt = rs.getTimestamp("last_used_at");
      token.setLastUsedAt(lastUsedAt != null ? lastUsedAt.toInstant() : null);
      token.setCreatedAt(rs.getTimestamp("created_at").toInstant());
      token.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
      return token;
    };
  }
}
