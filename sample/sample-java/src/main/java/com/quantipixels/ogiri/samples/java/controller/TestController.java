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
package com.quantipixels.ogiri.samples.java.controller;

import com.quantipixels.ogiri.samples.java.repository.SampleTokenRepository;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for exercising expiration flows in the sample app.
 *
 * <p>Not for production use. Exposes helpers that manipulate token state directly so the frontend
 * can drive the full expiry → 401 → redirect cycle without waiting for a real TTL to elapse.
 */
@RestController
@RequestMapping("/api/test")
@Profile("!jdbc")
public class TestController {

  private final SampleTokenRepository tokenRepository;
  private final OgiriUserDirectory userDirectory;

  public TestController(SampleTokenRepository tokenRepository, OgiriUserDirectory userDirectory) {
    this.tokenRepository = tokenRepository;
    this.userDirectory = userDirectory;
  }

  /**
   * Backdates the current session's {@code expiryAt} to one hour in the past so the next
   * authenticated request returns 401.
   *
   * <p>Requires a valid session (Ogiri filter must authenticate the request before this method
   * runs). The {@code client} header identifies the session to expire.
   */
  @PostMapping("/expire-token")
  public ResponseEntity<Map<String, String>> expireToken(
      Authentication authentication, HttpServletRequest request) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "Not authenticated"));
    }

    var user = userDirectory.findByUsername(authentication.getName());
    if (user == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "User not found"));
    }

    var client = request.getHeader("client");
    if (client == null || client.isBlank()) {
      return ResponseEntity.badRequest().body(Map.of("message", "Missing client header"));
    }

    var token = tokenRepository.findByUserIdAndClient(user.getOgiriUserId(), client);
    if (token.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("message", "Session not found"));
    }

    var entity = token.get();
    entity.setExpiryAt(Instant.now().minusSeconds(3600));
    tokenRepository.save(entity);

    return ResponseEntity.ok(Map.of("message", "Token expired"));
  }
}
