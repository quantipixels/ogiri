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

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

class UserDirectoryTest {

  private lateinit var userDirectory: SampleOgiriUserDirectory
  private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder()

  @BeforeEach
  fun setUp() {
    userDirectory = SampleOgiriUserDirectory(passwordEncoder)
  }

  @Test
  fun `loadUserByUsername returns user for valid username`() {
    val user = userDirectory.loadUserByUsername("user1")

    assertNotNull(user)
    assertEquals("user1", user.username)
    assertEquals(1L, user.getOgiriUserId())
  }

  @Test
  fun `loadUserByUsername throws exception for unknown username`() {
    assertThrows(IllegalArgumentException::class.java) {
      userDirectory.loadUserByUsername("nonexistent")
    }
  }

  @Test
  fun `findByEmail resolves known user and returns null for unknown`() {
    val found = userDirectory.findByEmail("user1@example.com")
    val missing = userDirectory.findByEmail("unknown@example.com")

    assertNotNull(found)
    assertEquals("user1", found!!.username)
    assertNull(missing)
  }

  @Test
  fun `loaded user has expected authorities and enabled flags`() {
    val user = userDirectory.loadUserByUsername("user1")

    assertEquals(1, user.authorities.size)
    assertTrue(user.authorities.contains(SimpleGrantedAuthority("ROLE_USER")))
    assertTrue(user.isEnabled)
    assertTrue(user.isAccountNonExpired)
    assertTrue(user.isAccountNonLocked)
    assertTrue(user.isCredentialsNonExpired)
  }

  @Test
  fun `recordSuccessfulLogin is a no-op for sample directory`() {
    assertDoesNotThrow { userDirectory.recordSuccessfulLogin(1L) }
  }
}
