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
import java.util.List;
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
  void routeRegistry_containsLoginRoute() {
    List<OgiriRoute> routes = routeRegistry.routes();

    boolean hasLoginRoute =
        routes.stream()
            .anyMatch(
                r -> r.getPath().equals("/api/auth/login") && r.getMethod() == HttpMethod.POST);

    assertTrue(hasLoginRoute, "Route registry should contain POST /api/auth/login");
  }

  @Test
  void routeRegistry_containsHealthRoute() {
    List<OgiriRoute> routes = routeRegistry.routes();

    boolean hasHealthRoute =
        routes.stream()
            .anyMatch(r -> r.getPath().equals("/api/health") && r.getMethod() == HttpMethod.GET);

    assertTrue(hasHealthRoute, "Route registry should contain GET /api/health");
  }

  @Test
  void routeRegistry_containsDocsRoute() {
    List<OgiriRoute> routes = routeRegistry.routes();

    boolean hasDocsRoute =
        routes.stream()
            .anyMatch(r -> r.getPath().equals("/api/docs/**") && r.getMethod() == HttpMethod.GET);

    assertTrue(hasDocsRoute, "Route registry should contain GET /api/docs/**");
  }

  @Test
  void routeRegistry_publicRoutesAreMarkedPublic() {
    List<OgiriRoute> routes = routeRegistry.routes();

    // useAuth = false means the route is public
    for (OgiriRoute route : routes) {
      assertFalse(
          route.getUseAuth(),
          "All sample routes should be public (useAuth=false): " + route.getPath());
    }
  }

  @Test
  void routeRegistry_hasExpectedNumberOfRoutes() {
    List<OgiriRoute> routes = routeRegistry.routes();

    assertEquals(3, routes.size(), "Should have exactly 3 public routes defined");
  }

  @Test
  void routeRegistry_routesHaveRateLimitingEnabled() {
    List<OgiriRoute> routes = routeRegistry.routes();

    // Java sample has rateLimit = true for public routes
    for (OgiriRoute route : routes) {
      assertTrue(
          route.getRateLimit(),
          "Sample routes should have rate limiting enabled: " + route.getPath());
    }
  }
}
