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

import com.quantipixels.ogiri.security.routes.Route
import com.quantipixels.ogiri.security.routes.RouteRegistry
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

/**
 * Sample RouteRegistry implementation for Kotlin.
 *
 * Declares public/unauthenticated routes that the filter should allow.
 */
@Component
class SampleRouteRegistry : RouteRegistry {
  override fun routes() =
      listOf(
          Route(HttpMethod.POST, "/api/auth/login", rateLimit = true, useAuth = false),
          Route(HttpMethod.GET, "/api/health", rateLimit = true, useAuth = false),
          Route(HttpMethod.GET, "/api/docs/**", rateLimit = true, useAuth = false),
      )
}
