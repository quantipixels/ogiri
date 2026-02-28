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

import com.quantipixels.ogiri.security.spi.OgiriUser;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Sample OgiriUserDirectory implementation for Java.
 *
 * <p>In a real application, this would load users from a database. This sample uses an in-memory
 * map for demonstration.
 */
@Component
public class SampleOgiriUserDirectory implements OgiriUserDirectory {

  private final Map<Long, SampleUser> usersById = new HashMap<>();
  private final Map<String, SampleUser> usersByUsername = new HashMap<>();

  public SampleOgiriUserDirectory(PasswordEncoder passwordEncoder) {
    String encodedPassword = passwordEncoder.encode("password");
    SampleUser user1 = new SampleUser(1L, "user1", encodedPassword, "user1@example.com");
    SampleUser user2 = new SampleUser(2L, "user2", encodedPassword, "user2@example.com");
    usersById.put(1L, user1);
    usersById.put(2L, user2);
    usersByUsername.put("user1", user1);
    usersByUsername.put("user2", user2);
  }

  @Override
  public OgiriUser loadUserByUsername(String username) {
    OgiriUser user = usersByUsername.get(username);
    if (user == null) {
      throw new IllegalArgumentException("User not found: " + username);
    }
    return user;
  }

  public OgiriUser findById(long id) {
    return usersById.get(id);
  }

  public OgiriUser findByEmail(String email) {
    return usersById.values().stream()
        .filter(u -> u.getEmail().equals(email))
        .findFirst()
        .orElse(null);
  }

  public OgiriUser findByUsername(String username) {
    return usersByUsername.get(username);
  }

  public void recordSuccessfulLogin(long userId) {
    // In a real application, update user last_login_at timestamp
  }

  /** Sample user implementation. */
  public static class SampleUser implements OgiriUser {
    private final long userId;
    private final String username;
    private final String password;
    private final String email;

    public SampleUser(Long userId, String username, String password, String email) {
      this.userId = userId;
      this.username = username;
      this.password = password;
      this.email = email;
    }

    public String getEmail() {
      return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return List.of(new SimpleGrantedAuthority("ROLE_USER"));
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

    @Override
    public long getOgiriUserId() {
      return userId;
    }
  }
}
