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

import java.time.Instant
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType

class OgiriJdbcTokenRepositoryTest {

  private val dataSource: DataSource =
      EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .addScript("classpath:ogiri/db/ogiri-user-tokens-h2.sql")
          .build()

  private val jdbcClient = JdbcClient.create(dataSource)

  private val repo =
      object : OgiriJdbcTokenRepository<OgiriBaseTokenRow>(jdbcClient) {
        override fun tableName() = "user_tokens"

        override fun rowMapper() = RowMapper { rs, _ ->
          OgiriBaseTokenRow(
                  id = rs.getLong("id"),
                  userId = rs.getLong("user_id"),
                  client = rs.getString("client_id"),
                  token = rs.getString("token_hash"),
                  tokenType = rs.getString("token_type"),
                  expiryAt = rs.getTimestamp("expiry_at").toInstant(),
                  tokenUpdatedAt = rs.getTimestamp("token_updated_at").toInstant(),
                  createdAt = rs.getTimestamp("created_at").toInstant(),
                  updatedAt = rs.getTimestamp("updated_at").toInstant(),
              )
              .apply {
                tokenSubtype = rs.getString("token_subtype")
                previousToken = rs.getString("previous_token_hash")
                lastToken = rs.getString("last_token_hash")
                lastUsedAt = rs.getTimestamp("last_used_at")?.toInstant()
              }
        }
      }

  @Test
  fun `save inserts new token and returns it with generated id`() {
    val token =
        OgiriBaseTokenRow(
            userId = 1L,
            client = "web",
            token = "hashedtoken",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        )

    val saved = repo.save(token)

    assertThat(saved.id).isGreaterThan(0L)
    assertThat(saved.userId).isEqualTo(1L)
    assertThat(saved.client).isEqualTo("web")
    assertThat(saved.token).isEqualTo("hashedtoken")
  }

  @Test
  fun `save updates existing token`() {
    val token =
        OgiriBaseTokenRow(
            userId = 10L,
            client = "desktop",
            token = "hash1",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        )
    val inserted = repo.save(token)
    inserted.token = "hash2"

    val updated = repo.save(inserted)

    assertThat(updated.id).isEqualTo(inserted.id)
    assertThat(updated.token).isEqualTo("hash2")
    val reloaded = repo.findById(updated.id).get()
    assertThat(reloaded.token).isEqualTo("hash2")
  }

  @Test
  fun `findById returns token when found`() {
    val token =
        repo.save(
            OgiriBaseTokenRow(
                userId = 2L,
                client = "mobile",
                token = "h",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            ))

    val found = repo.findById(token.id)

    assertThat(found).isPresent
    assertThat(found.get().id).isEqualTo(token.id)
    assertThat(found.get().userId).isEqualTo(2L)
  }

  @Test
  fun `findById returns empty when id does not exist`() {
    val result = repo.findById(999999L)
    assertThat(result).isEmpty
  }

  @Test
  fun `deleteById removes token`() {
    val token =
        repo.save(
            OgiriBaseTokenRow(
                userId = 3L,
                client = "api",
                token = "h",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            ))

    repo.deleteById(token.id)

    assertThat(repo.findById(token.id)).isEmpty
  }

  @Test
  fun `findByUserIdOrderByUpdatedAtDesc returns tokens newest first`() {
    val tokenA =
        repo.save(
            OgiriBaseTokenRow(
                userId = 20L,
                client = "alpha",
                token = "ha",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            ))
    val tokenB =
        repo.save(
            OgiriBaseTokenRow(
                userId = 20L,
                client = "beta",
                token = "hb",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            ))
    // Force tokenB to have a newer updatedAt
    tokenB.token = "hb-updated"
    repo.save(tokenB)

    val results = repo.findByUserIdOrderByUpdatedAtDesc(20L)

    assertThat(results).hasSize(2)
    assertThat(results[0].id).isEqualTo(tokenB.id)
    assertThat(results[1].id).isEqualTo(tokenA.id)
  }

  @Test
  fun `findByUserIdOrderByUpdatedAtDesc returns empty list when no tokens`() {
    val results = repo.findByUserIdOrderByUpdatedAtDesc(21L)
    assertThat(results).isEmpty()
  }

  @Test
  fun `findByUserIdAndClient returns token for matching pair`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 22L,
            client = "webapp",
            token = "hw",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    val result = repo.findByUserIdAndClient(22L, "webapp")

    assertThat(result).isPresent
    assertThat(result.get().userId).isEqualTo(22L)
    assertThat(result.get().client).isEqualTo("webapp")
  }

  @Test
  fun `findByUserIdAndClient returns empty when no match`() {
    val result = repo.findByUserIdAndClient(23L, "nonexistent")
    assertThat(result).isEmpty
  }

  @Test
  fun `findByUserIdAndClientIn returns matching tokens`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 30L,
            client = "c1",
            token = "h1",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 30L,
            client = "c2",
            token = "h2",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 30L,
            client = "c3",
            token = "h3",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    val results = repo.findByUserIdAndClientIn(30L, listOf("c1", "c2"))

    assertThat(results).hasSize(2)
    assertThat(results.map { it.client }).containsExactlyInAnyOrder("c1", "c2")
  }

  @Test
  fun `findByUserIdAndClientIn returns empty list when no match`() {
    val results = repo.findByUserIdAndClientIn(31L, listOf("none"))
    assertThat(results).isEmpty()
  }

  @Test
  fun `findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc returns tokens filtered by subtype`() {
    repo.save(
        OgiriBaseTokenRow(
                userId = 32L,
                client = "d1",
                token = "hd1",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            )
            .apply { tokenSubtype = "device" })
    repo.save(
        OgiriBaseTokenRow(
                userId = 32L,
                client = "d2",
                token = "hd2",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            )
            .apply { tokenSubtype = "device" })
    repo.save(
        OgiriBaseTokenRow(
                userId = 32L,
                client = "d3",
                token = "hd3",
                tokenType = "app",
                expiryAt = Instant.now().plusSeconds(3600),
            )
            .apply { tokenSubtype = "chat" })

    val results = repo.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(32L, "device")

    assertThat(results).hasSize(2)
    assertThat(results).allMatch { it.tokenSubtype == "device" }
  }

  // Slice 4 — Filter queries

  @Test
  fun `findByExpiryAtBefore returns expired tokens`() {
    val past = Instant.now().minusSeconds(1)
    val future = Instant.now().plusSeconds(3600)
    val expired =
        repo.save(
            OgiriBaseTokenRow(
                userId = 40L,
                client = "x",
                token = "h",
                tokenType = "app",
                expiryAt = past,
            ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 41L,
            client = "y",
            token = "h",
            tokenType = "app",
            expiryAt = future,
        ))
    val cutoff = Instant.now()

    val result = repo.findByExpiryAtBefore(cutoff)

    assertThat(result).anyMatch { it.id == expired.id }
    assertThat(result).noneMatch { it.expiryAt.isAfter(cutoff) }
  }

  @Test
  fun `findByExpiryAtBefore returns empty when nothing expired`() {
    assertThat(repo.findByExpiryAtBefore(Instant.now().minusSeconds(9999))).isEmpty()
  }

  @Test
  fun `findByTokenType returns tokens matching type`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 42L,
            client = "a",
            token = "h",
            tokenType = "sub",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 42L,
            client = "b",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    val subs = repo.findByTokenType("sub")

    assertThat(subs).isNotEmpty
    assertThat(subs).allMatch { it.tokenType == "sub" }
  }

  @Test
  fun `fetchTopExpiredBefore returns at most limit expired tokens`() {
    val past = Instant.now().minusSeconds(1)
    val future = Instant.now().plusSeconds(3600)
    repeat(5) { i ->
      repo.save(
          OgiriBaseTokenRow(
              userId = 70L,
              client = "c$i",
              token = "h$i",
              tokenType = "app",
              expiryAt = past.minusSeconds(i.toLong()),
          ))
    }
    repo.save(
        OgiriBaseTokenRow(
            userId = 70L,
            client = "future",
            token = "hf",
            tokenType = "app",
            expiryAt = future,
        ))

    val batch = repo.fetchTopExpiredBefore(Instant.now(), 3)

    assertThat(batch).hasSize(3)
    assertThat(batch).allMatch { it.expiryAt.isBefore(Instant.now()) }
  }

  @Test
  fun `fetchTopExpiredBefore returns only expired tokens when fewer than limit exist for user`() {
    val past = Instant.now().minusSeconds(1)
    val future = Instant.now().plusSeconds(3600)
    repeat(2) { i ->
      repo.save(
          OgiriBaseTokenRow(
              userId = 71L,
              client = "c$i",
              token = "h$i",
              tokenType = "app",
              expiryAt = past,
          ))
    }
    repo.save(
        OgiriBaseTokenRow(
            userId = 71L,
            client = "future-71",
            token = "hf71",
            tokenType = "app",
            expiryAt = future,
        ))

    val batch = repo.fetchTopExpiredBefore(Instant.now(), 100)

    // Our two expired tokens must be present; the future token must not be
    assertThat(batch.filter { it.userId == 71L }).hasSize(2)
    assertThat(batch.none { it.userId == 71L && it.client == "future-71" }).isTrue()
    assertThat(batch.size).isLessThanOrEqualTo(100)
  }

  // Slice 5 — Delete operations

  @Test
  fun `deleteByUserIdAndClient removes matching token`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 50L,
            client = "web",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 50L,
            client = "mobile",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    repo.deleteByUserIdAndClient(50L, "web")

    assertThat(repo.findByUserIdAndClient(50L, "web")).isEmpty
    assertThat(repo.findByUserIdAndClient(50L, "mobile")).isPresent
  }

  @Test
  fun `deleteByUserIdAndClientIn removes matching tokens`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 51L,
            client = "a",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 51L,
            client = "b",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 51L,
            client = "c",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    repo.deleteByUserIdAndClientIn(51L, listOf("a", "b"))

    assertThat(repo.findByUserIdOrderByUpdatedAtDesc(51L)).hasSize(1)
    assertThat(repo.findByUserIdAndClient(51L, "c")).isPresent
  }

  @Test
  fun `deleteByUserId removes all tokens for user`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 52L,
            client = "x",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 52L,
            client = "y",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    repo.deleteByUserId(52L)

    assertThat(repo.findByUserIdOrderByUpdatedAtDesc(52L)).isEmpty()
  }

  // Slice 6 — Aggregates

  @Test
  fun `countByUserId returns correct count`() {
    repo.save(
        OgiriBaseTokenRow(
            userId = 60L,
            client = "a",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 60L,
            client = "b",
            token = "h",
            tokenType = "app",
            expiryAt = Instant.now().plusSeconds(3600),
        ))

    assertThat(repo.countByUserId(60L)).isEqualTo(2L)
    assertThat(repo.countByUserId(99999L)).isEqualTo(0L)
  }

  @Test
  fun `deleteByExpiryAtBefore deletes expired tokens and returns count`() {
    val past = Instant.now().minusSeconds(1)
    val future = Instant.now().plusSeconds(3600)
    repo.save(
        OgiriBaseTokenRow(
            userId = 61L,
            client = "a",
            token = "h",
            tokenType = "app",
            expiryAt = past,
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 61L,
            client = "b",
            token = "h",
            tokenType = "app",
            expiryAt = past,
        ))
    repo.save(
        OgiriBaseTokenRow(
            userId = 61L,
            client = "c",
            token = "h",
            tokenType = "app",
            expiryAt = future,
        ))

    val deleted = repo.deleteByExpiryAtBefore(Instant.now())

    assertThat(deleted).isGreaterThanOrEqualTo(2)
    assertThat(repo.findByUserIdOrderByUpdatedAtDesc(61L)).hasSize(1)
  }
}
