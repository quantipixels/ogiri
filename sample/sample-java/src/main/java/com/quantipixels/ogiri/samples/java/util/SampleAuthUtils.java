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
package com.quantipixels.ogiri.samples.java.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;

/** Utility methods shared across demo controllers. */
public final class SampleAuthUtils {

  private SampleAuthUtils() {}

  /** Detects which Ogiri authentication method was used in the request. */
  public static String detectAuthMethod(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return "Bearer Token";
    }
    if (request.getCookies() != null
        && Arrays.stream(request.getCookies())
            .anyMatch(cookie -> "access-token".equals(cookie.getName()))) {
      return "Cookie";
    }
    if (request.getHeader("access-token") != null) {
      return "Header";
    }
    return "None";
  }

  /** Builds a base response map with the caller's authentication state. */
  public static Map<String, Object> authBase(Authentication authentication) {
    Map<String, Object> map = new HashMap<>();
    map.put("authenticated", authentication != null && authentication.isAuthenticated());
    map.put("principal", authentication != null ? authentication.getName() : "anonymous");
    return map;
  }

  /** Builds a base response map including the named authentication method. */
  public static Map<String, Object> authBase(String method, Authentication authentication) {
    Map<String, Object> map = authBase(authentication);
    map.put("method", method);
    return map;
  }
}
