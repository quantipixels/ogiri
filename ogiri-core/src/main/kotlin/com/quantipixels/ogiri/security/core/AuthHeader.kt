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
package com.quantipixels.ogiri.security.core

import com.fasterxml.jackson.core.JsonProcessingException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.IOException
import java.util.Base64
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AuthHeader::class.java)

const val ACCESS_TOKEN = "access-token"
const val CLIENT = "client"
const val UID = "uid"
const val TOKEN_TYPE = "token-type"
const val EXPIRY = "expiry"
const val ACCESS_TOKEN_KIND = "access-token-kind"

private val mapper = JsonCodec.mapper

data class AuthHeader(
    var accessToken: String? = null,
    var client: String? = null,
    var uid: String? = null,
    var expiry: String? = null,
    var kind: String? = null,
    var subTokens: Map<String, SubTokenHeader>? = null,
) {
  /**
           * Indicates whether the auth header contains non-blank accessToken, client, uid, and expiry.
           *
           * @return `true` if accessToken, client, uid, and expiry are all present and not blank, `false` otherwise.
           */
  fun isValid(): Boolean =
      !accessToken.isNullOrBlank() &&
          !client.isNullOrBlank() &&
          !uid.isNullOrBlank() &&
          !expiry.isNullOrBlank()
}

/** Sub-token header payload for arbitrary token types (see [OgiriSubTokenRegistration]). */
data class SubTokenHeader(
    val client: String? = null,
    val token: String? = null,
    val expiry: String? = null,
)

/**
     * Get the value of the cookie with the given name.
     *
     * @param name The cookie name to look up.
     * @return The cookie value, or `null` if no cookie with that name exists.
     */
    private fun HttpServletRequest.cookieValue(name: String): String? =
    cookies?.firstOrNull { it.name == name }?.value

/**
 * Extracts authentication values from the HTTP request, preferring explicit headers and falling back to cookies.
 *
 * Reads the ACCESS_TOKEN header first; if present, returns an AuthHeader populated from the ACCESS_TOKEN, CLIENT, UID,
 * EXPIRY and ACCESS_TOKEN_KIND request headers. If the ACCESS_TOKEN header is missing or blank, returns an AuthHeader
 * populated from the ACCESS_TOKEN, CLIENT, UID and EXPIRY cookies and the ACCESS_TOKEN_KIND header.
 *
 * @return An AuthHeader containing the extracted access token, client, uid, expiry, kind, and any defaulted subTokens.
 */
fun HttpServletRequest.extractAuthHeader(): AuthHeader {
  var accessToken = getHeader(ACCESS_TOKEN)
  if (!accessToken.isNullOrBlank()) {
    val client = getHeader(CLIENT)
    val uid = getHeader(UID)
    val expiry = getHeader(EXPIRY)
    val kind = getHeader(ACCESS_TOKEN_KIND)
    return AuthHeader(accessToken, client, uid, expiry, kind)
  }
  accessToken = cookieValue(ACCESS_TOKEN)
  val client = cookieValue(CLIENT)
  val uid = cookieValue(UID)
  val expiry = cookieValue(EXPIRY)
  val kind = getHeader(ACCESS_TOKEN_KIND)
  return AuthHeader(accessToken, client, uid, expiry, kind)
}

/**
 * Adds authentication headers to this response based on the provided AuthHeader.
 *
 * If `authHeaders` is null the response is left unchanged. For non-null input this sets standard headers
 * (access token, client, uid, token type "Bearer", token kind, expiry) when their values are present.
 * For each sub-token entry it emits two headers: the sub-token key with the sub-token value, and
 * `<sub-key>-authorization` containing a Base64-encoded JSON payload with `client`, `token`, and `expiry`.
 * If the main access token is present it also sets the `Authorization` header to `Bearer ` followed by
 * a Base64-encoded JSON payload containing `access_token`, `client`, `uid`, `token_type`, and `expiry`.
 *
 * @param authHeaders Authentication header data; when null no headers are added.
 */
fun HttpServletResponse.appendAuthHeaders(authHeaders: AuthHeader?) {
  if (authHeaders == null) return

  fun setIfNotBlank(
      name: String,
      value: String?,
  ) {
    if (!value.isNullOrBlank()) {
      setHeader(name, value)
    }
  }

  setIfNotBlank(ACCESS_TOKEN, authHeaders.accessToken)
  setIfNotBlank(CLIENT, authHeaders.client)
  setIfNotBlank(UID, authHeaders.uid)
  setIfNotBlank(TOKEN_TYPE, "Bearer")
  setIfNotBlank(ACCESS_TOKEN_KIND, authHeaders.kind)
  setIfNotBlank(EXPIRY, authHeaders.expiry)

  authHeaders.subTokens
      ?.takeIf { it.isNotEmpty() }
      ?.let { subs ->
        subs.forEach { (key, token) ->
          val payload =
              mapOf(
                  "client" to token.client,
                  "token" to token.token,
                  "expiry" to token.expiry,
              )
          val json = mapper.writeValueAsString(payload)
          val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
          setHeader(key, token.token)
          setHeader("$key-authorization", encoded)
        }
      }

  if (!authHeaders.accessToken.isNullOrBlank()) {
    val payload =
        mapOf(
            ACCESS_TOKEN to authHeaders.accessToken,
            CLIENT to authHeaders.client,
            UID to authHeaders.uid,
            TOKEN_TYPE to "Bearer",
            EXPIRY to authHeaders.expiry,
        )
    val json = mapper.writeValueAsString(payload)
    val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    setHeader("Authorization", "Bearer $encoded")
  }
}

/**
     * Parses an Authorization Bearer token string into a map of fields.
     *
     * Expects format `Bearer <base64-encoded-json>` where the decoded JSON contains string key/value pairs
     * such as `{"access-token":"...","client":"...","uid":"...","expiry":"..."}`.
     *
     * @param bearer The bearer token string, with or without the `Bearer ` prefix.
     * @return A map of parsed string fields, or `null` if base64 decoding or JSON parsing fails.
     */
fun parseBearerToken(bearer: String): Map<String, String>? =
    try {
      val token = bearer.trim().removePrefix("Bearer ").trim()
      val json = String(Base64.getDecoder().decode(token), Charsets.UTF_8)
      @Suppress("UNCHECKED_CAST")
      mapper.readValue(json, Map::class.java) as? Map<String, String>
    } catch (e: IllegalArgumentException) {
      logger.debug("Failed to decode Base64 bearer token: {}", e.message)
      null
    } catch (e: JsonProcessingException) {
      logger.debug("Failed to parse bearer token JSON: {}", e.message)
      null
    } catch (e: IOException) {
      logger.debug("I/O error parsing bearer token: {}", e.message)
      null
    }
