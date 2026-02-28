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
package com.quantipixels.ogiri.samples.kotlin.util

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication

/** Detects which Ogiri authentication method was used in the request. */
fun detectAuthMethod(request: HttpServletRequest): String =
    when {
      request.getHeader("Authorization")?.startsWith("Bearer ") == true -> "Bearer Token"
      request.cookies?.any { it.name == "access-token" } == true -> "Cookie"
      request.getHeader("access-token") != null -> "Header"
      else -> "None"
    }

/** Builds a base response map with the caller's authentication state. */
fun authBase(authentication: Authentication?): Map<String, Any> =
    mapOf(
        "authenticated" to (authentication != null && authentication.isAuthenticated),
        "principal" to (authentication?.name ?: "anonymous"),
    )

/** Builds a base response map including the named authentication method. */
fun authBase(method: String, authentication: Authentication?): Map<String, Any> =
    mapOf("method" to method) + authBase(authentication)
