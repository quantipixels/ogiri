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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserDirectoryTest {

  private SampleOgiriUserDirectory userDirectory;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    userDirectory = new SampleOgiriUserDirectory(passwordEncoder);
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
  void findByEmail_resolvesKnownUserAndReturnsNullForUnknown() {
    OgiriUser found = userDirectory.findByEmail("user1@example.com");
    OgiriUser missing = userDirectory.findByEmail("unknown@example.com");

    assertNotNull(found);
    assertEquals("user1", found.getUsername());
    assertNull(missing);
  }

  @Test
  void loadedUser_hasExpectedAuthoritiesAndEnabledFlags() {
    OgiriUser user = userDirectory.loadUserByUsername("user1");

    assertEquals(1, user.getAuthorities().size());
    assertTrue(user.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    assertTrue(user.isEnabled());
    assertTrue(user.isAccountNonExpired());
    assertTrue(user.isAccountNonLocked());
    assertTrue(user.isCredentialsNonExpired());
  }

  @Test
  void recordSuccessfulLogin_isNoOpForSampleDirectory() {
    assertDoesNotThrow(() -> userDirectory.recordSuccessfulLogin(1L));
  }
}
