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
package com.quantipixels.ogiri.jdbc

import com.quantipixels.ogiri.security.tokens.OgiriBaseToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import java.time.Instant
import java.util.Optional
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder

/**
 * Abstract [JdbcClient]-based implementation of [OgiriTokenRepository].
 *
 * Provides all standard query and mutation operations using ANSI SQL with named parameters. Column
 * names are fixed (see [OgiriBaseTokenRow]). Insert vs. update is detected by `id == 0L`.
 *
 * Subclasses must implement:
 * - [tableName]: unqualified table name (e.g., `"tokens"`)
 * - [rowMapper]: maps all token columns to a [OgiriBaseToken] subtype
 *
 * Example:
 * ```kotlin
 * @Repository
 * class MyTokenRepository(client: JdbcClient) : OgiriJdbcTokenRepository<MyTokenRow>(client) {
 *     override fun tableName() = "user_tokens"
 *     override fun rowMapper() = RowMapper { rs, _ ->
 *         MyTokenRow(id = rs.getLong("id"), userId = rs.getLong("user_id"), ...)
 *     }
 * }
 * ```
 */
abstract class OgiriJdbcTokenRepository<T : OgiriBaseToken>(
    protected val jdbcClient: JdbcClient,
) : OgiriTokenRepository<T> {

  /**
   * The database table name used for all queries in this repository. Must not include a schema
   * prefix unless your datasource targets a fixed schema.
   */
  abstract fun tableName(): String

  /**
   * Maps a [java.sql.ResultSet] row to an instance of [T]. Must read all columns defined on
   * [OgiriBaseTokenRow] (`id`, `user_id`, `client_id`, `token_hash`, `token_type`, `token_subtype`,
   * `expiry_at`, `previous_token_hash`, `last_token_hash`, `token_updated_at`, `last_used_at`,
   * `created_at`, `updated_at`).
   */
  abstract fun rowMapper(): RowMapper<T>

  override fun <S : T> save(token: S): S {
    if (token.id == 0L) {
      val keyHolder = GeneratedKeyHolder()
      jdbcClient
          .sql(
              "INSERT INTO ${tableName()} (user_id, client_id, token_hash, token_type, token_subtype, expiry_at, previous_token_hash, last_token_hash, token_updated_at, last_used_at, created_at, updated_at) VALUES (:userId, :client, :token, :tokenType, :tokenSubtype, :expiryAt, :previousToken, :lastToken, :tokenUpdatedAt, :lastUsedAt, :createdAt, :updatedAt)")
          .param("userId", token.userId)
          .param("client", token.client)
          .param("token", token.token)
          .param("tokenType", token.tokenType)
          .param("tokenSubtype", token.tokenSubtype)
          .param("expiryAt", token.expiryAt)
          .param("previousToken", token.previousToken)
          .param("lastToken", token.lastToken)
          .param("tokenUpdatedAt", token.tokenUpdatedAt)
          .param("lastUsedAt", token.lastUsedAt)
          .param("createdAt", token.createdAt)
          .param("updatedAt", token.updatedAt)
          .update(keyHolder)
      token.id = (keyHolder.keys!!["ID"] as Number).toLong()
      return token
    } else {
      val now = Instant.now()
      token.updatedAt = now
      jdbcClient
          .sql(
              "UPDATE ${tableName()} SET token_hash = :token, token_type = :tokenType, token_subtype = :tokenSubtype, expiry_at = :expiryAt, previous_token_hash = :previousToken, last_token_hash = :lastToken, token_updated_at = :tokenUpdatedAt, last_used_at = :lastUsedAt, updated_at = :updatedAt WHERE id = :id")
          .param("token", token.token)
          .param("tokenType", token.tokenType)
          .param("tokenSubtype", token.tokenSubtype)
          .param("expiryAt", token.expiryAt)
          .param("previousToken", token.previousToken)
          .param("lastToken", token.lastToken)
          .param("tokenUpdatedAt", token.tokenUpdatedAt)
          .param("lastUsedAt", token.lastUsedAt)
          .param("updatedAt", token.updatedAt)
          .param("id", token.id)
          .update()
      return token
    }
  }

  override fun findById(id: Long): Optional<T> =
      jdbcClient
          .sql("SELECT * FROM ${tableName()} WHERE id = :id")
          .param("id", id)
          .query(rowMapper())
          .optional()

  override fun deleteById(id: Long) {
    jdbcClient.sql("DELETE FROM ${tableName()} WHERE id = :id").param("id", id).update()
  }

  override fun delete(token: T) = deleteById(token.id)

  override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T> =
      jdbcClient
          .sql("SELECT * FROM ${tableName()} WHERE user_id = :userId ORDER BY updated_at DESC")
          .param("userId", userId)
          .query(rowMapper())
          .list()

  override fun findByUserIdAndClient(userId: Long, client: String): Optional<T> =
      jdbcClient
          .sql("SELECT * FROM ${tableName()} WHERE user_id = :userId AND client_id = :client")
          .param("userId", userId)
          .param("client", client)
          .query(rowMapper())
          .optional()

  override fun findByUserIdAndClientIn(userId: Long, clients: Collection<String>): List<T> {
    if (clients.isEmpty()) return emptyList()
    return jdbcClient
        .sql("SELECT * FROM ${tableName()} WHERE user_id = :userId AND client_id IN (:clients)")
        .param("userId", userId)
        .param("clients", clients)
        .query(rowMapper())
        .list()
  }

  override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(
      userId: Long,
      tokenSubtype: String,
  ): List<T> =
      jdbcClient
          .sql(
              "SELECT * FROM ${tableName()} WHERE user_id = :userId AND token_subtype = :tokenSubtype ORDER BY updated_at DESC")
          .param("userId", userId)
          .param("tokenSubtype", tokenSubtype)
          .query(rowMapper())
          .list()

  override fun findByExpiryAtBefore(cutoff: Instant): List<T> =
      jdbcClient
          .sql("SELECT * FROM ${tableName()} WHERE expiry_at < :cutoff")
          .param("cutoff", cutoff)
          .query(rowMapper())
          .list()

  override fun findByTokenType(tokenType: String): List<T> =
      jdbcClient
          .sql("SELECT * FROM ${tableName()} WHERE token_type = :tokenType")
          .param("tokenType", tokenType)
          .query(rowMapper())
          .list()

  override fun deleteByUserIdAndClient(userId: Long, client: String) {
    jdbcClient
        .sql("DELETE FROM ${tableName()} WHERE user_id = :userId AND client_id = :client")
        .param("userId", userId)
        .param("client", client)
        .update()
  }

  override fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>) {
    if (clients.isEmpty()) return
    jdbcClient
        .sql("DELETE FROM ${tableName()} WHERE user_id = :userId AND client_id IN (:clients)")
        .param("userId", userId)
        .param("clients", clients)
        .update()
  }

  override fun deleteByUserId(userId: Long) {
    jdbcClient
        .sql("DELETE FROM ${tableName()} WHERE user_id = :userId")
        .param("userId", userId)
        .update()
  }

  override fun countByUserId(userId: Long): Long =
      jdbcClient
          .sql("SELECT COUNT(*) FROM ${tableName()} WHERE user_id = :userId")
          .param("userId", userId)
          .query(Long::class.java)
          .single()

  override fun deleteByExpiryAtBefore(cutoff: Instant): Int =
      jdbcClient
          .sql("DELETE FROM ${tableName()} WHERE expiry_at < :cutoff")
          .param("cutoff", cutoff)
          .update()
}
