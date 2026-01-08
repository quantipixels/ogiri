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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller showcasing different authentication methods.
 *
 * <p>This controller demonstrates that Ogiri Security supports three authentication methods: 1.
 * HTTP Headers (access-token, client, uid, expiry) 2. Secure Cookies (same fields as cookies) 3.
 * Bearer Token (Authorization: Bearer base64-json)
 *
 * <p>All three methods are functionally equivalent and work with the same authentication filter.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

  /**
   * Demonstrate header-based authentication.
   *
   * <p>This endpoint shows authentication via HTTP headers. The client sends: - access-token: The
   * token value - client: The client identifier - uid: The user identifier - expiry: Token
   * expiration timestamp
   *
   * <p>Example:
   *
   * <pre>
   * curl http://localhost:8080/api/demo/headers \
   *   -H "access-token: &lt;token&gt;" \
   *   -H "client: &lt;client&gt;" \
   *   -H "uid: &lt;uid&gt;" \
   *   -H "expiry: &lt;expiry&gt;"
   * </pre>
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract headers
   * @return Authentication details and original headers
   */
  @GetMapping("/headers")
  public ResponseEntity<Map<String, Object>> demonstrateHeaderAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> headerInfo = new HashMap<>();
    headerInfo.put("method", "HTTP Headers");
    headerInfo.put("authenticated", authentication != null && authentication.isAuthenticated());
    headerInfo.put("principal", authentication != null ? authentication.getName() : "anonymous");

    Map<String, String> receivedHeaders = new HashMap<>();
    receivedHeaders.put("access-token", request.getHeader("access-token"));
    receivedHeaders.put("client", request.getHeader("client"));
    receivedHeaders.put("uid", request.getHeader("uid"));
    receivedHeaders.put("expiry", request.getHeader("expiry"));
    headerInfo.put("receivedHeaders", receivedHeaders);

    return ResponseEntity.ok(headerInfo);
  }

  /**
   * Demonstrate cookie-based authentication.
   *
   * <p>This endpoint shows authentication via secure cookies. The client sends cookies: -
   * access-token: The token value - client: The client identifier - uid: The user identifier -
   * expiry: Token expiration timestamp
   *
   * <p>Cookies are automatically sent by the browser if set by the server with proper attributes
   * (HttpOnly, Secure, SameSite).
   *
   * <p>Example:
   *
   * <pre>
   * curl http://localhost:8080/api/demo/cookies \
   *   -b "access-token=&lt;token&gt;;client=&lt;client&gt;;uid=&lt;uid&gt;;expiry=&lt;expiry&gt;"
   * </pre>
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract cookies
   * @return Authentication details and received cookies
   */
  @GetMapping("/cookies")
  public ResponseEntity<Map<String, Object>> demonstrateCookieAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> cookieInfo = new HashMap<>();
    cookieInfo.put("method", "Secure Cookies");
    cookieInfo.put("authenticated", authentication != null && authentication.isAuthenticated());
    cookieInfo.put("principal", authentication != null ? authentication.getName() : "anonymous");

    Map<String, String> cookies = new HashMap<>();
    if (request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        cookies.put(cookie.getName(), cookie.getValue());
      }
    }

    Map<String, String> receivedCookies = new HashMap<>();
    receivedCookies.put("access-token", cookies.get("access-token"));
    receivedCookies.put("client", cookies.get("client"));
    receivedCookies.put("uid", cookies.get("uid"));
    receivedCookies.put("expiry", cookies.get("expiry"));
    cookieInfo.put("receivedCookies", receivedCookies);

    return ResponseEntity.ok(cookieInfo);
  }

  /**
   * Demonstrate Bearer token authentication.
   *
   * <p>This endpoint shows authentication via the Authorization header with a Bearer token. The
   * token is a Base64-encoded JSON object containing access-token, client, uid, and expiry.
   *
   * <p>Example:
   *
   * <pre>
   * # First, create the Bearer token (after login):
   * # Get the Authorization header value from login response
   *
   * curl http://localhost:8080/api/demo/bearer \
   *   -H "Authorization: Bearer &lt;base64-encoded-json&gt;"
   * </pre>
   *
   * <p>The Bearer token format is:
   *
   * <pre>
   * Authorization: Bearer eyJhY2Nlc3MtdG9rZW4iOiIuLi4iLCJjbGllbnQiOiIuLi4iLCJ1aWQiOiIuLi4iLCJleHBpcnkiOiIuLi4ifQ==
   * </pre>
   *
   * @param authentication Injected by Spring Security after successful auth
   * @param request HTTP request to extract Authorization header
   * @return Authentication details and Bearer token info
   */
  @GetMapping("/bearer")
  public ResponseEntity<Map<String, Object>> demonstrateBearerAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> bearerInfo = new HashMap<>();
    bearerInfo.put("method", "Bearer Token");
    bearerInfo.put("authenticated", authentication != null && authentication.isAuthenticated());
    bearerInfo.put("principal", authentication != null ? authentication.getName() : "anonymous");

    String authHeader = request.getHeader("Authorization");
    bearerInfo.put("authorizationHeader", authHeader != null ? authHeader : "Not provided");
    bearerInfo.put(
        "note", "Bearer token is Base64-encoded JSON with access-token, client, uid, expiry");

    return ResponseEntity.ok(bearerInfo);
  }

  /**
   * General authentication info endpoint.
   *
   * <p>This endpoint works with any authentication method (headers, cookies, or Bearer token). It
   * demonstrates that the authentication method is transparent to the application logic.
   *
   * <p>Example:
   *
   * <pre>
   * # Works with any auth method:
   * curl http://localhost:8080/api/demo/info -H "access-token: &lt;token&gt;" ...
   * curl http://localhost:8080/api/demo/info -b "access-token=&lt;token&gt;;..."
   * curl http://localhost:8080/api/demo/info -H "Authorization: Bearer &lt;token&gt;"
   * </pre>
   *
   * @param authentication Injected by Spring Security
   * @param request HTTP request for additional context
   * @return Current authentication state
   */
  @GetMapping("/info")
  public ResponseEntity<Map<String, Object>> getAuthInfo(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> authInfo = new HashMap<>();
    authInfo.put("authenticated", authentication != null && authentication.isAuthenticated());
    authInfo.put("principal", authentication != null ? authentication.getName() : "anonymous");

    List<String> authorities =
        authentication != null
            ? authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList())
            : List.of();
    authInfo.put("authorities", authorities);
    authInfo.put("authMethod", detectAuthMethod(request));
    authInfo.put(
        "message", "This endpoint accepts authentication via headers, cookies, or Bearer token");

    return ResponseEntity.ok(authInfo);
  }

  /**
   * Detect which authentication method was used.
   *
   * @param request HTTP request
   * @return Detected authentication method
   */
  private String detectAuthMethod(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return "Bearer Token";
    }

    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      boolean hasCookie =
          Arrays.stream(cookies).anyMatch(cookie -> "access-token".equals(cookie.getName()));
      if (hasCookie) {
        return "Cookie";
      }
    }

    if (request.getHeader("access-token") != null) {
      return "Header";
    }

    return "None";
  }
}
