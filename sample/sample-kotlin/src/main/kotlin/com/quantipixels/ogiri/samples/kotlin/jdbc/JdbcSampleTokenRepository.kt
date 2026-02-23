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
package com.quantipixels.ogiri.samples.kotlin.jdbc

import com.quantipixels.ogiri.jdbc.OgiriJdbcTokenRepository
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * JDBC repository for JdbcSampleToken.
 *
 * Extend [OgiriJdbcTokenRepository] and provide:
 * - [tableName]: the target table (must match your schema)
 * - [rowMapper]: maps a ResultSet row to your token class
 *
 * All 15 OgiriTokenRepository methods are handled by the base class via JdbcClient.
 */
@Repository
@Profile("jdbc")
class JdbcSampleTokenRepository(jdbcClient: JdbcClient) :
    OgiriJdbcTokenRepository<JdbcSampleToken>(jdbcClient) {

  override fun tableName() = "user_tokens"

  override fun rowMapper() = RowMapper { rs, _ ->
    JdbcSampleToken().apply {
      id = rs.getLong("id")
      userId = rs.getLong("user_id")
      client = rs.getString("client_id")
      token = rs.getString("token_hash")
      tokenType = rs.getString("token_type")
      tokenSubtype = rs.getString("token_subtype")
      expiryAt = rs.getTimestamp("expiry_at").toInstant()
      previousToken = rs.getString("previous_token_hash")
      lastToken = rs.getString("last_token_hash")
      tokenUpdatedAt = rs.getTimestamp("token_updated_at").toInstant()
      lastUsedAt = rs.getTimestamp("last_used_at")?.toInstant()
      createdAt = rs.getTimestamp("created_at").toInstant()
      updatedAt = rs.getTimestamp("updated_at").toInstant()
    }
  }
}
