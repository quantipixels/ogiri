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

import com.quantipixels.ogiri.security.core.SecurityServiceException;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import com.quantipixels.ogiri.security.tokens.OgiriTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller demonstrating login and logout flows.
 *
 * <p>This controller shows how to integrate with OgiriTokenService for authentication operations.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final OgiriTokenService<?> tokenService;
  private final OgiriUserDirectory userDirectory;

  public AuthController(OgiriTokenService<?> tokenService, OgiriUserDirectory userDirectory) {
    this.tokenService = tokenService;
    this.userDirectory = userDirectory;
  }

  /**
   * Login endpoint that validates credentials and returns authentication tokens.
   *
   * <p>On successful login, this endpoint: 1. Validates username and password 2. Creates
   * authentication tokens (APP + sub-tokens) 3. Returns tokens in response headers AND body 4. Sets
   * secure cookies if cookie config is enabled
   *
   * <p>Example request:
   *
   * <pre>
   * curl -X POST http://localhost:8080/api/auth/login \
   *   -H "Content-Type: application/json" \
   *   -d '{"username":"user1","password":"password"}'
   * </pre>
   *
   * <p>Response includes: - Headers: access-token, client, uid, expiry, Authorization (Bearer) -
   * Cookies: access-token, client, uid, expiry (if enabled) - Body: JSON with token details
   *
   * @param request Login credentials
   * @param httpRequest HTTP request for context
   * @param httpResponse HTTP response for setting headers/cookies
   * @return Authentication response with token details
   */
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(
      @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      // Verify credentials using tokenService.verifyUser
      tokenService.verifyUser(
          httpRequest,
          httpResponse,
          request.username(), // Using username as email for this sample
          request.password());

      // Extract response headers set by verifyUser
      String accessToken = httpResponse.getHeader("access-token");
      String client = httpResponse.getHeader("client");
      String uid = httpResponse.getHeader("uid");
      String expiry = httpResponse.getHeader("expiry");

      return ResponseEntity.ok(
          new AuthResponse(
              accessToken != null ? accessToken : "",
              client != null ? client : "",
              uid != null ? uid : "",
              expiry != null ? expiry : "",
              "Login successful"));
    } catch (SecurityServiceException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new AuthResponse("", "", "", "", "Invalid credentials"));
    }
  }

  /**
   * Logout endpoint that revokes the current authentication token.
   *
   * <p>This endpoint: 1. Extracts the current token from request headers/cookies 2. Revokes the
   * token and all associated sub-tokens 3. Clears authentication cookies
   *
   * <p>Example request:
   *
   * <pre>
   * curl -X POST http://localhost:8080/api/auth/logout \
   *   -H "access-token: &lt;token&gt;" \
   *   -H "client: &lt;client&gt;" \
   *   -H "uid: &lt;uid&gt;" \
   *   -H "expiry: &lt;expiry&gt;"
   * </pre>
   *
   * @param authentication Current authentication context
   * @param httpRequest HTTP request for extracting token
   * @param httpResponse HTTP response for clearing cookies
   * @return Logout confirmation message
   */
  @PostMapping("/logout")
  public ResponseEntity<java.util.Map<String, String>> logout(
      Authentication authentication,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(java.util.Map.of("message", "Not authenticated"));
    }

    try {
      var user = userDirectory.findByUsername(authentication.getName());
      if (user != null) {
        tokenService.revokeClient(user.getOgiriUserId(), httpRequest, httpResponse);
      }
      return ResponseEntity.ok(java.util.Map.of("message", "Logout successful"));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(java.util.Map.of("message", "Logout failed: " + e.getMessage()));
    }
  }

  /** Login request body. */
  public record LoginRequest(String username, String password) {}

  /** Authentication response body. */
  public record AuthResponse(
      String accessToken, String client, String uid, String expiry, String message) {}
}
