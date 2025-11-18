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
package com.quantipixels.ogiri.security.tokens

import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.spi.TokenUser
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class TokenServiceSubTokenTest {
  private lateinit var repository: InMemoryTokenRepository
  private lateinit var tokenService: TokenService<Token>
  private val passwordEncoder: PasswordEncoder = NoOpPasswordEncoder.getInstance()
  private val identifierPolicy =
    object : IdentifierPolicy {
      private val counter = AtomicLong(0)

      override fun generate(): String = "tok-${counter.incrementAndGet()}"

      override fun isValid(value: String?): Boolean = !value.isNullOrBlank()
    }

  private val user = TestTokenUser(userId = 1L, _username = "user")
  private val userDirectory =
    object : TokenUserDirectory {
      override fun loadUserByUsername(username: String): TestTokenUser = user

      override fun findById(id: Long): TestTokenUser? = user.takeIf { it.userId == id }

      override fun findByEmail(email: String): TestTokenUser? = null

      override fun findByUsername(username: String): TestTokenUser? = user.takeIf { it.username == username }

      override fun recordSuccessfulLogin(userId: Long) {}
    }

  @BeforeEach
  fun setup() {
    repository = InMemoryTokenRepository()
  }

  @Test
  fun `default sub token is issued and returned in headers`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers: AuthHeader = tokenService.createNewAuthToken(user.userId, "clientA")

    val chatToken = repository.findByUserIdAndClient(user.userId, "clientA.chat")
    assertNotNull(chatToken)
    assertEquals(TokenType.SUB, chatToken!!.tokenType)
    assertEquals("chat", chatToken.tokenSubtype)
    assertNotNull(headers.subTokens?.get("chat"))
  }

  @Test
  fun `opt-in sub token is only created when requested`() {
    val registry =
      DefaultSubTokenRegistry(listOf(chatRegistration(), deviceRegistration(includeByDefault = false)))
    tokenService = TokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    val headers = tokenService.createNewAuthToken(user.userId, "web")
    assertNull(repository.findByUserIdAndClient(user.userId, "web.device"))

    val req =
      MockHttpServletRequest().apply {
        addHeader("access-token", headers.accessToken)
        addHeader("client", headers.client)
        addHeader("uid", headers.uid)
        addHeader("expiry", headers.expiry)
      }
    val res = MockHttpServletResponse()

    tokenService.renewSubToken(user.userId, req, res, "device")

    assertNotNull(repository.findByUserIdAndClient(user.userId, "web.device"))
    assertNotNull(res.getHeader("sub-tokens"))
  }

  @Test
  fun `validateSubToken accepts bearer payload`() {
    val registry = DefaultSubTokenRegistry(listOf(chatRegistration()))
    tokenService = TokenService(repository, passwordEncoder, userDirectory, identifierPolicy, registry)

    tokenService.createNewAuthToken(user.userId, "clientZ")
    val chat = repository.findByUserIdAndClient(user.userId, "clientZ.chat")!!
    val bearerPayload =
      mapOf(
        "client" to requireNotNull(chat.client),
        "token" to requireNotNull(chat.plainToken),
        "expiry" to chat.expiryAt.toString(),
      )
    val bearerJson = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(bearerPayload)
    val bearer =
      "Bearer " + java.util.Base64.getEncoder().encodeToString(bearerJson.toByteArray(Charsets.UTF_8))

    val ok = tokenService.validateSubToken(user.username, "chat", bearer)
    assertEquals(true, ok)
    val okRaw = tokenService.validateSubToken(user.username, "chat", chat.plainToken!!)
    assertEquals(true, okRaw)
  }

  private fun chatRegistration(): SubTokenRegistration =
    object : SubTokenRegistration {
      override val name: String = "chat"
      override val includeByDefault: Boolean = true

      override fun clientIdFor(parentClientId: String): String = "$parentClientId.chat"

      override fun expiry(parentExpiry: Instant): Instant = parentExpiry
    }

  private fun deviceRegistration(includeByDefault: Boolean) =
    object : SubTokenRegistration {
      override val name: String = "device"
      override val includeByDefault: Boolean = includeByDefault

      override fun clientIdFor(parentClientId: String): String = "$parentClientId.device"

      override fun expiry(parentExpiry: Instant): Instant = parentExpiry
    }
}

private class TestTokenUser(
  override val userId: Long,
  val _username: String,
) : TokenUser {
  override fun getAuthorities(): MutableCollection<out GrantedAuthority> = mutableListOf(SimpleGrantedAuthority("USER"))

  override fun getPassword(): String = "pw"

  override fun getUsername(): String = _username

  override fun isAccountNonExpired(): Boolean = true

  override fun isAccountNonLocked(): Boolean = true

  override fun isCredentialsNonExpired(): Boolean = true

  override fun isEnabled(): Boolean = true
}

@Suppress("DEPRECATION")
private class InMemoryTokenRepository : TokenRepository<Token> {
  private val store = mutableListOf<Token>()
  private val idSeq = AtomicLong(1)

  override fun findAllByUserId(userId: Long): List<Token> = store.filter { it.userId == userId }

  override fun findByUserIdAndClient(
    userId: Long,
    clientId: String,
  ): Token? = store.firstOrNull { it.userId == userId && it.client == clientId }

  override fun deleteByUserIdAndClient(
    userId: Long,
    clientId: String,
  ) {
    store.removeIf { it.userId == userId && it.client == clientId }
  }

  override fun deleteByUserIdAndClientIn(
    userId: Long,
    clientIds: Collection<String>,
  ) {
    store.removeIf { it.userId == userId && it.client in clientIds }
  }

  override fun deleteByUserId(userId: Long) {
    store.removeIf { it.userId == userId }
  }

  override fun findByExpiryAtBefore(cutoff: Instant): List<Token> = store.filter { it.expiryAt.isBefore(cutoff) }

  override fun <S : Token> save(entity: S): S {
    if (entity!!.id == 0L) {
      val newId = idSeq.getAndIncrement()
      entity.id = newId
      store.add(entity)
    } else {
      deleteById(entity.id)
      store.add(entity)
    }
    return entity
  }

  override fun <S : Token> saveAll(entities: MutableIterable<S>): MutableList<S> {
    return entities.map { save(it) }.toMutableList()
  }

  override fun findById(id: Long): Optional<Token> = Optional.ofNullable(store.firstOrNull { it.id == id })

  override fun existsById(id: Long): Boolean = store.any { it.id == id }

  override fun findAll(): MutableList<Token> = store.toMutableList()

  override fun findAllById(ids: MutableIterable<Long>): MutableList<Token> =
    store.filter { it.id in ids.toSet() }.toMutableList()

  override fun count(): Long = store.size.toLong()

  override fun deleteById(id: Long) {
    store.removeIf { it.id == id }
  }

  override fun delete(entity: Token) {
    store.removeIf { it.id == entity.id }
  }

  override fun deleteAllById(ids: MutableIterable<Long>) {
    val set = ids.toSet()
    store.removeIf { it.id in set }
  }

  override fun deleteAll(entities: MutableIterable<Token>) {
    val set = entities.map { it.id }.toSet()
    store.removeIf { it.id in set }
  }

  override fun deleteAll() {
    store.clear()
  }

  // Unused Paging/Sorting/JPA methods for these unit tests
  override fun findAll(sort: org.springframework.data.domain.Sort): MutableList<Token> =
    throw UnsupportedOperationException()

  override fun <S : Token?> findAll(example: org.springframework.data.domain.Example<S>): MutableList<S> =
    throw UnsupportedOperationException()

  override fun <S : Token?> findAll(
    example: org.springframework.data.domain.Example<S>,
    sort: org.springframework.data.domain.Sort,
  ): MutableList<S> = throw UnsupportedOperationException()

  override fun findAll(
    pageable: org.springframework.data.domain.Pageable,
  ): org.springframework.data.domain.Page<Token> =
    org.springframework.data.domain.PageImpl(findAll(), pageable, count())

  override fun <S : Token> findOne(example: org.springframework.data.domain.Example<S>): Optional<S> =
    throw UnsupportedOperationException()

  override fun <S : Token> count(example: org.springframework.data.domain.Example<S>): Long =
    throw UnsupportedOperationException()

  override fun <S : Token> exists(example: org.springframework.data.domain.Example<S>): Boolean =
    throw UnsupportedOperationException()

  override fun flush() {}

  override fun <S : Token> saveAndFlush(entity: S): S = save(entity)

  override fun <S : Token> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = saveAll(entities)

  override fun deleteAllInBatch(entities: MutableIterable<Token>) = deleteAll(entities)

  override fun deleteAllInBatch() = deleteAll()

  override fun deleteAllByIdInBatch(ids: MutableIterable<Long>) = deleteAllById(ids)

  override fun getOne(id: Long): Token = findById(id).orElseThrow()

  override fun getById(id: Long): Token = getOne(id)

  override fun getReferenceById(id: Long): Token = getOne(id)

  override fun <S : Token> findAll(
    example: org.springframework.data.domain.Example<S>,
    pageable: org.springframework.data.domain.Pageable,
  ): org.springframework.data.domain.Page<S> = throw UnsupportedOperationException()

  override fun <S : Token, R : Any?> findBy(
    example: org.springframework.data.domain.Example<S>,
    queryFunction: java.util.function.Function<
      org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>,
      R,
    >,
  ): R = throw UnsupportedOperationException()
}
