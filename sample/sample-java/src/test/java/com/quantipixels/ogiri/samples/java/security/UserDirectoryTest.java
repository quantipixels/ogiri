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
package com.quantipixels.ogiri.samples.java.security;

import static org.junit.jupiter.api.Assertions.*;

import com.quantipixels.ogiri.security.spi.OgiriUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class UserDirectoryTest {

  private SampleOgiriUserDirectory userDirectory;

  @BeforeEach
  void setUp() {
    userDirectory = new SampleOgiriUserDirectory();
  }

  @Test
  void loadUserByUsername_returnsUserForValidUsername() {
    OgiriUser user = userDirectory.loadUserByUsername("user1");

    assertNotNull(user);
    assertEquals("user1", user.getUsername());
    assertEquals(1L, user.getOgiriUserId());
  }

  @Test
  void loadUserByUsername_throwsExceptionForUnknownUsername() {
    assertThrows(
        IllegalArgumentException.class, () -> userDirectory.loadUserByUsername("nonexistent"));
  }

  @Test
  void findById_returnsUserForValidId() {
    OgiriUser user = userDirectory.findById(1L);

    assertNotNull(user);
    assertEquals("user1", user.getUsername());
    assertEquals(1L, user.getOgiriUserId());
  }

  @Test
  void findById_returnsNullForUnknownId() {
    OgiriUser user = userDirectory.findById(999L);

    assertNull(user);
  }

  @Test
  void findByEmail_returnsUserForValidEmail() {
    OgiriUser user = userDirectory.findByEmail("user1@example.com");

    assertNotNull(user);
    assertEquals("user1", user.getUsername());
  }

  @Test
  void findByEmail_returnsNullForUnknownEmail() {
    OgiriUser user = userDirectory.findByEmail("unknown@example.com");

    assertNull(user);
  }

  @Test
  void findByUsername_returnsUserForValidUsername() {
    OgiriUser user = userDirectory.findByUsername("user2");

    assertNotNull(user);
    assertEquals("user2", user.getUsername());
    assertEquals(2L, user.getOgiriUserId());
  }

  @Test
  void findByUsername_returnsNullForUnknownUsername() {
    OgiriUser user = userDirectory.findByUsername("nonexistent");

    assertNull(user);
  }

  @Test
  void userHasCorrectAuthorities() {
    OgiriUser user = userDirectory.loadUserByUsername("user1");

    assertNotNull(user.getAuthorities());
    assertEquals(1, user.getAuthorities().size());
    assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Test
  void userIsEnabled() {
    OgiriUser user = userDirectory.loadUserByUsername("user1");

    assertTrue(user.isEnabled());
    assertTrue(user.isAccountNonExpired());
    assertTrue(user.isAccountNonLocked());
    assertTrue(user.isCredentialsNonExpired());
  }

  @Test
  void recordSuccessfulLogin_doesNotThrow() {
    assertDoesNotThrow(() -> userDirectory.recordSuccessfulLogin(1L));
  }

  @Test
  void multipleUsersExist() {
    OgiriUser user1 = userDirectory.findById(1L);
    OgiriUser user2 = userDirectory.findById(2L);

    assertNotNull(user1);
    assertNotNull(user2);
    assertNotEquals(user1.getUsername(), user2.getUsername());
    assertEquals("user1", user1.getUsername());
    assertEquals("user2", user2.getUsername());
  }
}
