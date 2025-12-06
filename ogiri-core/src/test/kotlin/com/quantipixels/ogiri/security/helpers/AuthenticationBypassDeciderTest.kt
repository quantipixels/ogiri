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
package com.quantipixels.ogiri.security.helpers

import com.quantipixels.ogiri.security.routes.Route
import com.quantipixels.ogiri.security.routes.RouteCatalog
import com.quantipixels.ogiri.security.routes.RouteRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AuthenticationBypassDeciderTest {
  @AfterEach
  fun clearContext() {
    SecurityContextHolder.clearContext()
  }

  @Test
  fun `bypasses authenticated requests`() {
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken("user", "pw", listOf(SimpleGrantedAuthority("USER")))

    val catalog = RouteCatalog(emptyList())
    val decider = AuthenticationBypassDecider(catalog)

    val request = MockHttpServletRequest("GET", "/any")
    assertTrue(decider.canSkip(request))
  }

  @Test
  fun `bypasses whitelisted actuator route`() {
    val catalog = RouteCatalog(emptyList())
    val decider = AuthenticationBypassDecider(catalog)
    val request = MockHttpServletRequest("GET", "/actuator/health")

    assertTrue(decider.canSkip(request))
  }

  @Test
  fun `bypasses OPTIONS preflight`() {
    val catalog = RouteCatalog(emptyList())
    val decider = AuthenticationBypassDecider(catalog)
    val request = MockHttpServletRequest("OPTIONS", "/api/anything")

    assertTrue(decider.canSkip(request))
  }

  @Test
  fun `bypasses public route from catalog`() {
    val registry =
        object : RouteRegistry {
          override fun routes(): List<Route> =
              listOf(Route.get("/public", useAuth = false), Route.get("/private", useAuth = true))
        }
    val catalog = RouteCatalog(listOf(registry))
    val decider = AuthenticationBypassDecider(catalog)

    val publicRequest = MockHttpServletRequest("GET", "/public")
    val privateRequest = MockHttpServletRequest("GET", "/private")

    assertTrue(decider.canSkip(publicRequest))
    assertFalse(decider.canSkip(privateRequest))
  }

  @Test
  fun `route catalog matches templated paths`() {
    val registry =
        object : RouteRegistry {
          override fun routes(): List<Route> = listOf(Route.get("/users/{id}", useAuth = false))
        }
    val catalog = RouteCatalog(listOf(registry))

    assertTrue(catalog.isPublicRoute("/users/123", HttpMethod.GET))
    assertFalse(catalog.isPublicRoute("/users/123", HttpMethod.POST))
    assertFalse(catalog.isPublicRoute("/projects/123", HttpMethod.GET))
  }
}
