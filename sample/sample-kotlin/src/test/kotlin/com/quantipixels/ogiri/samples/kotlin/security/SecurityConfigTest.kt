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
  fun `route registry exposes exact public routes with rate limiting enabled`() {
    val routes = routeRegistry.routes()
    val actual =
        routes
            .map { route ->
              listOf(route.method.name(), route.path, route.useAuth, route.rateLimit)
            }
            .toSet()
    val expected =
        setOf(
            listOf(HttpMethod.POST.name(), "/api/auth/login", false, true),
            listOf(HttpMethod.GET.name(), "/api/health", false, true),
            listOf(HttpMethod.GET.name(), "/api/docs/**", false, true),
        )

    assertEquals(expected, actual)
  }
}
