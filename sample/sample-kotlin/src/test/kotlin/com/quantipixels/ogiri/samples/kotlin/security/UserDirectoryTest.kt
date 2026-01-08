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
import org.junit.jupiter.api.Assertions.assertNotEquals
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
  fun `findById returns user for valid id`() {
    val user = userDirectory.findById(1L)

    assertNotNull(user)
    assertEquals("user1", user!!.username)
    assertEquals(1L, user.getOgiriUserId())
  }

  @Test
  fun `findById returns null for unknown id`() {
    val user = userDirectory.findById(999L)

    assertNull(user)
  }

  @Test
  fun `findByEmail returns user for valid email`() {
    val user = userDirectory.findByEmail("user1@example.com")

    assertNotNull(user)
    assertEquals("user1", user!!.username)
  }

  @Test
  fun `findByEmail returns null for unknown email`() {
    val user = userDirectory.findByEmail("unknown@example.com")

    assertNull(user)
  }

  @Test
  fun `findByUsername returns user for valid username`() {
    val user = userDirectory.findByUsername("user2")

    assertNotNull(user)
    assertEquals("user2", user!!.username)
    assertEquals(2L, user.getOgiriUserId())
  }

  @Test
  fun `findByUsername returns null for unknown username`() {
    val user = userDirectory.findByUsername("nonexistent")

    assertNull(user)
  }

  @Test
  fun `user has correct authorities`() {
    val user = userDirectory.loadUserByUsername("user1")

    assertNotNull(user.authorities)
    assertEquals(1, user.authorities.size)
    assertTrue(user.authorities.contains(SimpleGrantedAuthority("ROLE_USER")))
  }

  @Test
  fun `user is enabled`() {
    val user = userDirectory.loadUserByUsername("user1")

    assertTrue(user.isEnabled)
    assertTrue(user.isAccountNonExpired)
    assertTrue(user.isAccountNonLocked)
    assertTrue(user.isCredentialsNonExpired)
  }

  @Test
  fun `recordSuccessfulLogin does not throw`() {
    assertDoesNotThrow { userDirectory.recordSuccessfulLogin(1L) }
  }

  @Test
  fun `multiple users exist`() {
    val user1 = userDirectory.findById(1L)
    val user2 = userDirectory.findById(2L)

    assertNotNull(user1)
    assertNotNull(user2)
    assertNotEquals(user1!!.username, user2!!.username)
    assertEquals("user1", user1.username)
    assertEquals("user2", user2.username)
  }

  @Test
  fun `user has correct ogiri user id`() {
    val user = userDirectory.loadUserByUsername("user1")

    assertEquals(1L, user.getOgiriUserId())
  }
}
