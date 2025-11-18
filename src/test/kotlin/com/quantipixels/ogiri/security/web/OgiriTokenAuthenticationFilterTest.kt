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
package com.quantipixels.ogiri.security.web

import com.quantipixels.ogiri.security.core.AuthHeader
import com.quantipixels.ogiri.security.core.DefaultIdentifierPolicy
import com.quantipixels.ogiri.security.helpers.AuthenticationBypassDecider
import com.quantipixels.ogiri.security.routes.Route
import com.quantipixels.ogiri.security.routes.RouteCatalog
import com.quantipixels.ogiri.security.routes.RouteRegistry
import com.quantipixels.ogiri.security.spi.TokenUser
import com.quantipixels.ogiri.security.spi.TokenUserDirectory
import com.quantipixels.ogiri.security.tokens.DefaultSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.Token
import com.quantipixels.ogiri.security.tokens.TokenRepository
import com.quantipixels.ogiri.security.tokens.TokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OgiriTokenAuthenticationFilterTest {
  private val passwordEncoder: PasswordEncoder = NoOpPasswordEncoder.getInstance()
  private val identifierPolicy = DefaultIdentifierPolicy()

  private val user = TestTokenUser(userId = 1L, _username = "user")
  private val userDirectory =
    object : TokenUserDirectory {
      override fun loadUserByUsername(username: String): TestTokenUser = user

      override fun findById(id: Long): TestTokenUser? = user.takeIf { it.userId == id }

      override fun findByEmail(email: String): TestTokenUser? = null

      override fun findByUsername(username: String): TestTokenUser? = user.takeIf { it.username == username }

      override fun recordSuccessfulLogin(userId: Long) {}
    }

  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  @Test
  fun `filter bypasses when decider allows`() {
    val bypassRoutes =
      RouteCatalog(
        listOf(
          object : RouteRegistry {
            override fun routes() = listOf(Route.get("/public", useAuth = false))
          },
        ),
      )
    val bypassDecider = AuthenticationBypassDecider(bypassRoutes)
    val entryPoint = RecordingEntryPoint()
    val filter = newFilter(NoopTokenRepository(), bypassDecider, entryPoint).filter

    val request = MockHttpServletRequest("GET", "/public")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    filter.doFilter(request, response, chain)

    assertNull(SecurityContextHolder.getContext().authentication)
    // Entry point should never be called on bypass
    assertNull(entryPoint.lastRequest)
  }

  @Test
  fun `filter authenticates within batch window without rotation`() {
    val fixture = newFilter()

    // Issue a token using the same TokenService used by the filter
    val headers: AuthHeader = fixture.tokenService.createNewAuthToken(user.userId, "clientA")
    val issuedToken = fixture.repository.findByUserIdAndClient(user.userId, "clientA")!!

    val request = MockHttpServletRequest("GET", "/api/secure")
    request.addHeader("access-token", headers.accessToken!!)
    request.addHeader("client", headers.client!!)
    request.addHeader("uid", headers.uid!!)
    request.addHeader("expiry", headers.expiry!!)
    request.addHeader("access-token-kind", "APP")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    fixture.filter.doFilter(request, response, chain)

    // Authentication should be present
    assertNotNull(SecurityContextHolder.getContext().authentication)
    // Batch window requests should record activity on the token
    assertNotNull(issuedToken.lastUsedAt)
    // Entry point should not be called
    assertNull(fixture.entryPoint.lastRequest)
    // Freshly issued tokens are treated as batch requests; no rotation headers are appended
    assertNull(response.getHeader("access-token"))
  }

  @Test
  fun `filter rotates tokens outside batch window`() {
    val fixture = newFilter()

    val headers: AuthHeader = fixture.tokenService.createNewAuthToken(user.userId, "clientA")
    val stored = fixture.repository.findByUserIdAndClient(user.userId, "clientA")!!
    // Simulate a stale request outside the batch grace window
    stored.updatedAt = Instant.now().minusSeconds(10)

    val request = MockHttpServletRequest("POST", "/api/secure")
    request.addHeader("access-token", headers.accessToken!!)
    request.addHeader("client", headers.client!!)
    request.addHeader("uid", headers.uid!!)
    request.addHeader("expiry", headers.expiry!!)
    request.addHeader("access-token-kind", "APP")
    val response = MockHttpServletResponse()
    val chain = MockFilterChain()

    fixture.filter.doFilter(request, response, chain)

    assertNotNull(SecurityContextHolder.getContext().authentication)
    assertNull(fixture.entryPoint.lastRequest)
    // Outside the batch window, the filter should rotate and emit new headers
    assertNotNull(response.getHeader("access-token"))
  }

  private data class FilterFixture(
    val repository: TokenRepository<Token>,
    val tokenService: TokenService<Token>,
    val entryPoint: RecordingEntryPoint,
    val filter: OgiriTokenAuthenticationFilter,
  )

  private fun newFilter(
    repository: TokenRepository<Token> = InMemoryTokenRepository(),
    bypassDecider: AuthenticationBypassDecider = AuthenticationBypassDecider(RouteCatalog(emptyList())),
    entryPoint: RecordingEntryPoint = RecordingEntryPoint(),
  ): FilterFixture {
    val tokenService =
      TokenService(
        repository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        DefaultSubTokenRegistry(emptyList()),
      )
    val filter =
      OgiriTokenAuthenticationFilter(
        userDirectory,
        tokenService,
        entryPoint,
        bypassDecider,
        identifierPolicy,
      )
    return FilterFixture(repository, tokenService, entryPoint, filter)
  }
}

private class RecordingEntryPoint : AuthenticationEntryPoint {
  var lastRequest: HttpServletRequest? = null

  override fun commence(
    request: HttpServletRequest,
    response: HttpServletResponse,
    authException: org.springframework.security.core.AuthenticationException,
  ) {
    lastRequest = request
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
    if (entity.id == 0L) {
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

@Suppress("DEPRECATION")
private class NoopTokenRepository : TokenRepository<Token> {
  override fun findAllByUserId(userId: Long): List<Token> = emptyList()

  override fun findByUserIdAndClient(
    userId: Long,
    clientId: String,
  ): Token? = null

  override fun deleteByUserIdAndClient(
    userId: Long,
    clientId: String,
  ) {}

  override fun deleteByUserIdAndClientIn(
    userId: Long,
    clientIds: Collection<String>,
  ) {}

  override fun deleteByUserId(userId: Long) {}

  override fun findByExpiryAtBefore(cutoff: Instant): List<Token> = emptyList()

  override fun <S : Token> save(entity: S): S = entity

  override fun <S : Token> saveAll(entities: MutableIterable<S>): MutableList<S> = entities.toMutableList()

  override fun findById(id: Long): Optional<Token> = Optional.empty()

  override fun existsById(id: Long): Boolean = false

  override fun findAll(): MutableList<Token> = mutableListOf()

  override fun findAllById(ids: MutableIterable<Long>): MutableList<Token> = mutableListOf()

  override fun count(): Long = 0

  override fun deleteById(id: Long) {}

  override fun delete(entity: Token) {}

  override fun deleteAllById(ids: MutableIterable<Long>) {}

  override fun deleteAll(entities: MutableIterable<Token>) {}

  override fun deleteAll() {}

  override fun findAll(sort: org.springframework.data.domain.Sort): MutableList<Token> = mutableListOf()

  override fun <S : Token?> findAll(example: org.springframework.data.domain.Example<S>): MutableList<S> =
    mutableListOf()

  override fun <S : Token?> findAll(
    example: org.springframework.data.domain.Example<S>,
    sort: org.springframework.data.domain.Sort,
  ): MutableList<S> = mutableListOf()

  override fun findAll(
    pageable: org.springframework.data.domain.Pageable,
  ): org.springframework.data.domain.Page<Token> = org.springframework.data.domain.Page.empty()

  override fun <S : Token> findOne(example: org.springframework.data.domain.Example<S>): Optional<S> = Optional.empty()

  override fun <S : Token> count(example: org.springframework.data.domain.Example<S>): Long = 0

  override fun <S : Token> exists(example: org.springframework.data.domain.Example<S>): Boolean = false

  override fun flush() {}

  override fun <S : Token> saveAndFlush(entity: S): S = entity

  override fun <S : Token> saveAllAndFlush(entities: MutableIterable<S>): MutableList<S> = entities.toMutableList()

  override fun deleteAllInBatch(entities: MutableIterable<Token>) {}

  override fun deleteAllInBatch() {}

  override fun deleteAllByIdInBatch(ids: MutableIterable<Long>) {}

  override fun getOne(id: Long): Token = throw UnsupportedOperationException()

  override fun getById(id: Long): Token = throw UnsupportedOperationException()

  override fun getReferenceById(id: Long): Token = throw UnsupportedOperationException()

  override fun <S : Token> findAll(
    example: org.springframework.data.domain.Example<S>,
    pageable: org.springframework.data.domain.Pageable,
  ): org.springframework.data.domain.Page<S> = org.springframework.data.domain.Page.empty()

  override fun <S : Token, R : Any?> findBy(
    example: org.springframework.data.domain.Example<S>,
    queryFunction: java.util.function.Function<
      org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>,
      R,
    >,
  ): R = throw UnsupportedOperationException()
}
