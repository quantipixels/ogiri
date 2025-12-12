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
package com.quantipixels.ogiri.security.routes

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.springframework.http.HttpMethod

/** Describes an HTTP route for public/auth and rate-limit configuration. */
class OgiriRoute(
    val method: HttpMethod,
    val path: String,
    val rateLimit: Boolean = true,
    val useAuth: Boolean = true,
    val rateLimitPermitsPerMinute: Long? = null,
) {
  init {
    require(path.startsWith('/')) { "Path must start with '/'" }
  }

  private val braceVarRegex = Regex("\\{([a-zA-Z0-9_\\-]+)\\}")
  private val colonVarRegex = Regex(":([a-zA-Z0-9_\\-]+)")

  private fun encode(value: Any): String =
      URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.toString())

  fun apply(params: Map<String, Any>): String {
    var result = path
    braceVarRegex.findAll(path).forEach { match ->
      val name = match.groupValues[1]
      val value = params[name] ?: throw IllegalArgumentException("Missing path param '$name'")
      result = result.replace("{$name}", encode(value))
    }
    colonVarRegex.findAll(result).forEach { match ->
      val name = match.groupValues[1]
      val value = params[name] ?: throw IllegalArgumentException("Missing path param '$name'")
      result = result.replace(":$name", encode(value))
    }
    return result
  }

  fun apply(vararg params: Pair<String, Any>): String = apply(params.toMap())

  fun pathWithWildcardVariables(): String {
    var pattern = path
    pattern = braceVarRegex.replace(pattern) { "*" }
    pattern = colonVarRegex.replace(pattern) { "*" }
    return pattern
  }

  companion object {
    fun get(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.GET, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    fun post(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.POST, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    fun put(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.PUT, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    fun patch(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.PATCH, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    fun delete(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.DELETE, path, rateLimit, useAuth, rateLimitPermitsPerMinute)
  }
}
