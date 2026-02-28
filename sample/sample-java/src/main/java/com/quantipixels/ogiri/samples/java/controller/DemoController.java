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

import com.quantipixels.ogiri.samples.java.util.SampleAuthUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller showcasing different authentication methods.
 *
 * <p>Ogiri Security supports three authentication methods, all functionally equivalent:
 *
 * <ol>
 *   <li>HTTP Headers (access-token, client, uid, expiry)
 *   <li>Secure Cookies (same four fields)
 *   <li>Bearer Token (Authorization: Bearer base64-json)
 * </ol>
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

  /** Shows authentication via HTTP headers (access-token, client, uid, expiry). */
  @GetMapping("/headers")
  public ResponseEntity<Map<String, Object>> demonstrateHeaderAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> result = SampleAuthUtils.authBase("HTTP Headers", authentication);

    Map<String, String> receivedHeaders = new HashMap<>();
    receivedHeaders.put("access-token", request.getHeader("access-token"));
    receivedHeaders.put("client", request.getHeader("client"));
    receivedHeaders.put("uid", request.getHeader("uid"));
    receivedHeaders.put("expiry", request.getHeader("expiry"));
    result.put("receivedHeaders", receivedHeaders);

    return ResponseEntity.ok(result);
  }

  /** Shows authentication via secure cookies (same four fields set as HttpOnly cookies). */
  @GetMapping("/cookies")
  public ResponseEntity<Map<String, Object>> demonstrateCookieAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> result = SampleAuthUtils.authBase("Secure Cookies", authentication);

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
    result.put("receivedCookies", receivedCookies);

    return ResponseEntity.ok(result);
  }

  /** Shows authentication via Authorization: Bearer (Base64-encoded JSON). */
  @GetMapping("/bearer")
  public ResponseEntity<Map<String, Object>> demonstrateBearerAuth(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> result = SampleAuthUtils.authBase("Bearer Token", authentication);

    String authHeader = request.getHeader("Authorization");
    result.put("authorizationHeader", authHeader != null ? authHeader : "Not provided");
    result.put(
        "note", "Bearer token is Base64-encoded JSON with access-token, client, uid, expiry");

    return ResponseEntity.ok(result);
  }

  /** General info endpoint that works with any authentication method. */
  @GetMapping("/info")
  public ResponseEntity<Map<String, Object>> getAuthInfo(
      Authentication authentication, HttpServletRequest request) {
    Map<String, Object> result = SampleAuthUtils.authBase(authentication);

    List<String> authorities =
        authentication != null
            ? authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
            : List.of();
    result.put("authorities", authorities);
    result.put("authMethod", SampleAuthUtils.detectAuthMethod(request));
    result.put(
        "message", "This endpoint accepts authentication via headers, cookies, or Bearer token");

    return ResponseEntity.ok(result);
  }
}
