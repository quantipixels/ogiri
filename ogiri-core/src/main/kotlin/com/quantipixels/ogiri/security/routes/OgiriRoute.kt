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

  /**
   * URL-encodes the given value using UTF-8.
   *
   * @param value The value to encode; it is converted to a string before encoding.
   * @return The percent-encoded string representation of the value using UTF-8.
   */
  private fun encode(value: Any): String =
      URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.toString())

  /**
   * Substitutes path variable placeholders in this route's path with the provided parameter values.
   *
   * @param params A map from variable name (without braces or leading colon) to the value to
   *   insert; values are converted to strings and URL-encoded.
   * @return The route path with all `{name}` and `:name` placeholders replaced by their
   *   corresponding URL-encoded values.
   * @throws IllegalArgumentException If a placeholder in the path has no corresponding entry in
   *   `params`.
   */
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
    /**
     * Create an OgiriRoute configured for HTTP GET requests.
     *
     * @param path The route path; must start with '/'.
     * @param rateLimit Whether rate limiting is enabled for the route.
     * @param useAuth Whether authentication is required for the route.
     * @param rateLimitPermitsPerMinute Optional quota of permits per minute for rate limiting.
     * @return An OgiriRoute representing the GET route with the specified options.
     */
    fun get(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.GET, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    /**
     * Creates an OgiriRoute configured for HTTP POST using the given path and options.
     *
     * @param path The route path; must start with '/'.
     * @param rateLimit Whether rate limiting is applied.
     * @param useAuth Whether authentication is required.
     * @param rateLimitPermitsPerMinute Optional rate limit quota (permits per minute); when null
     *   the default quota is used.
     * @return An OgiriRoute configured for the POST method with the provided path and options.
     */
    fun post(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.POST, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    /**
     * Create an OgiriRoute configured for HTTP PUT requests.
     *
     * @param path The route path; must start with '/'.
     * @param rateLimit Whether to apply rate limiting for this route.
     * @param useAuth Whether authentication is required for this route.
     * @param rateLimitPermitsPerMinute Optional rate limit quota in permits per minute.
     * @return An OgiriRoute for the PUT method targeting `path` with the specified options.
     */
    fun put(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.PUT, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    /**
     * Creates an OgiriRoute configured for the HTTP PATCH method.
     *
     * @param path Route path; must start with `/` and may contain path variables like `{id}` or
     *   `:id`.
     * @param rateLimit Whether rate limiting is enabled for this route.
     * @param useAuth Whether authentication is required for this route.
     * @param rateLimitPermitsPerMinute Optional explicit permits-per-minute quota for rate
     *   limiting.
     * @return An OgiriRoute instance using the PATCH method with the given settings.
     */
    fun patch(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.PATCH, path, rateLimit, useAuth, rateLimitPermitsPerMinute)

    /**
     * Create an OgiriRoute for HTTP DELETE with the given path and options.
     *
     * @param path Route path; must start with '/'.
     * @param rateLimit Whether rate limiting is applied.
     * @param useAuth Whether authentication is required.
     * @param rateLimitPermitsPerMinute Optional rate limit quota (permits per minute).
     * @return An OgiriRoute configured with HTTP DELETE and the provided options.
     */
    fun delete(
        path: String,
        rateLimit: Boolean = true,
        useAuth: Boolean = true,
        rateLimitPermitsPerMinute: Long? = null,
    ) = OgiriRoute(HttpMethod.DELETE, path, rateLimit, useAuth, rateLimitPermitsPerMinute)
  }
}
