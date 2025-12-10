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
package com.quantipixels.ogiri.samples.kotlin.security

import com.quantipixels.ogiri.security.spi.OgiriUser
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component

/**
 * Sample OgiriUserDirectory implementation for Kotlin.
 *
 * In a real application, this would load users from a database. This sample uses an in-memory map
 * for demonstration.
 */
@Component
class SampleOgiriUserDirectory : OgiriUserDirectory {
  private val usersByUsername = mutableMapOf<String, SampleUser>()
  private val usersById = mutableMapOf<Long, SampleUser>()

  init {
    val user1 = SampleUser(1L, "user1", "password", "user1@example.com")
    val user2 = SampleUser(2L, "user2", "password", "user2@example.com")
    usersByUsername["user1"] = user1
    usersByUsername["user2"] = user2
    usersById[1L] = user1
    usersById[2L] = user2
  }

  override fun loadUserByUsername(username: String): OgiriUser =
      usersByUsername[username] ?: throw IllegalArgumentException("User not found: $username")

  override fun findById(id: Long): OgiriUser? = usersById[id]

  override fun findByEmail(email: String): OgiriUser? =
      usersById.values.firstOrNull { it.email == email }

  override fun findByUsername(username: String): OgiriUser? = usersByUsername[username]

  override fun recordSuccessfulLogin(userId: Long) {
    // In a real application, update user last_login_at timestamp
  }

  /** Sample user implementation for Kotlin */
  private data class SampleUser(
      private val id: Long,
      private val username: String,
      private val password: String,
      val email: String,
  ) : OgiriUser {
    override val userId: Long = id

    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_USER"))

    override fun getPassword(): String = password

    override fun getUsername(): String = username

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true
  }
}
