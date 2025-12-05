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

import com.quantipixels.ogiri.security.spi.TokenUser;
import com.quantipixels.ogiri.security.spi.TokenUserDirectory;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Sample TokenUserDirectory implementation for Java.
 *
 * In a real application, this would load users from a database.
 * This sample uses an in-memory map for demonstration.
 */
@Component
public class SampleTokenUserDirectory implements TokenUserDirectory {

  private static final Map<Long, SampleUser> USERS_BY_ID = new HashMap<>();
  private static final Map<String, SampleUser> USERS_BY_USERNAME = new HashMap<>();

  static {
    SampleUser user1 = new SampleUser(1L, "user1", "password", "user1@example.com");
    SampleUser user2 = new SampleUser(2L, "user2", "password", "user2@example.com");
    USERS_BY_ID.put(1L, user1);
    USERS_BY_ID.put(2L, user2);
    USERS_BY_USERNAME.put("user1", user1);
    USERS_BY_USERNAME.put("user2", user2);
  }

  @Override
  public TokenUser loadUserByUsername(String username) {
    TokenUser user = USERS_BY_USERNAME.get(username);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + username);
    }
    return user;
  }

  public TokenUser findById(long id) {
    return USERS_BY_ID.get(id);
  }

  public TokenUser findByEmail(String email) {
    return USERS_BY_ID.values().stream()
        .filter(u -> u.getEmail().equals(email))
        .findFirst()
        .orElse(null);
  }

  public TokenUser findByUsername(String username) {
    return USERS_BY_USERNAME.get(username);
  }

  public void recordSuccessfulLogin(long userId) {
    // In a real application, update user last_login_at timestamp
  }

  /** Sample user implementation */
  public static class SampleUser implements TokenUser {
    private final long userId;  // Property for Kotlin interface
    private final String username;
    private final String password;
    private final String email;

    public SampleUser(Long userId, String username, String password, String email) {
      this.userId = userId;
      this.username = username;
      this.password = password;
      this.email = email;
    }

    // Kotlin property getter for TokenUser interface
    public long getUserId() {
      return userId;
    }

    public String getEmail() {
      return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return java.util.Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
      return password;
    }

    @Override
    public String getUsername() {
      return username;
    }

    @Override
    public boolean isAccountNonExpired() {
      return true;
    }

    @Override
    public boolean isAccountNonLocked() {
      return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
