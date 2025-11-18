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

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.Base64

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
  /** Simple validity check to prevent running verification against empty headers. */
  fun isValid(): Boolean =
    !accessToken.isNullOrBlank() &&
      !client.isNullOrBlank() &&
      !uid.isNullOrBlank() &&
      !expiry.isNullOrBlank()
}

/** Sub-token header payload for arbitrary token types (see [SubTokenRegistration]). */
data class SubTokenHeader(
  val client: String? = null,
  val token: String? = null,
  val expiry: String? = null,
)

private fun HttpServletRequest.cookieValue(name: String): String? = cookies?.firstOrNull { it.name == name }?.value

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

  authHeaders.subTokens?.takeIf { it.isNotEmpty() }?.let { subs ->
    val payload =
      subs.mapValues { (_, token) ->
        mapOf(
          "client" to token.client,
          "token" to token.token,
          "expiry" to token.expiry,
        )
      }
    val json = mapper.writeValueAsString(payload)
    val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
    setHeader("sub-tokens", encoded)
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
