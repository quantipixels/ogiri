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
package com.quantipixels.ogiri.samples.kotlin.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class SecurityConfigTest {

  private lateinit var routeRegistry: SampleRouteRegistry

  @BeforeEach
  fun setUp() {
    routeRegistry = SampleRouteRegistry()
  }

  @Test
  fun `route registry contains login route`() {
    val routes = routeRegistry.routes()

    val hasLoginRoute = routes.any { it.path == "/api/auth/login" && it.method == HttpMethod.POST }

    assertTrue(hasLoginRoute, "Route registry should contain POST /api/auth/login")
  }

  @Test
  fun `route registry contains health route`() {
    val routes = routeRegistry.routes()

    val hasHealthRoute = routes.any { it.path == "/api/health" && it.method == HttpMethod.GET }

    assertTrue(hasHealthRoute, "Route registry should contain GET /api/health")
  }

  @Test
  fun `route registry contains docs route`() {
    val routes = routeRegistry.routes()

    val hasDocsRoute = routes.any { it.path == "/api/docs/**" && it.method == HttpMethod.GET }

    assertTrue(hasDocsRoute, "Route registry should contain GET /api/docs/**")
  }

  @Test
  fun `route registry public routes are marked public`() {
    val routes = routeRegistry.routes()

    for (route in routes) {
      // useAuth = false means it's a public route
      assertTrue(
          !route.useAuth, "All sample routes should be public (useAuth=false): ${route.path}")
    }
  }

  @Test
  fun `route registry has expected number of routes`() {
    val routes = routeRegistry.routes()

    assertEquals(3, routes.size, "Should have exactly 3 public routes defined")
  }

  @Test
  fun `route registry routes are rate limited`() {
    val routes = routeRegistry.routes()

    for (route in routes) {
      assertTrue(route.rateLimit, "Sample routes should be rate limited: ${route.path}")
    }
  }
}
