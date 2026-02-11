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

import com.quantipixels.ogiri.security.routes.OgiriRoute;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class SecurityConfigTest {

  private SampleRouteRegistry routeRegistry;

  @BeforeEach
  void setUp() {
    routeRegistry = new SampleRouteRegistry();
  }

  @Test
  void routeRegistry_exposesExactPublicRoutesWithRateLimitingEnabled() {
    List<OgiriRoute> routes = routeRegistry.routes();
    Set<List<Object>> actual = new HashSet<>();
    for (OgiriRoute route : routes) {
      actual.add(
          List.of(
              route.getMethod().name(), route.getPath(), route.getUseAuth(), route.getRateLimit()));
    }
    Set<List<Object>> expected =
        Set.of(
            List.of(HttpMethod.POST.name(), "/api/auth/login", false, true),
            List.of(HttpMethod.GET.name(), "/api/health", false, true),
            List.of(HttpMethod.GET.name(), "/api/docs/**", false, true));

    assertEquals(expected, actual);
  }
}
