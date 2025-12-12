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
package com.quantipixels.ogiri.security.tokens

/**
 * Filter tokens by their type (APP or SUB).
 *
 * @param type The token type to filter by
 * @return List of tokens matching the given type
 */
fun <T : OgiriToken> Collection<T>.filterByOgiriTokenType(type: OgiriTokenType): List<T> = filter {
  OgiriTokenType.of(it.tokenType) == type
}

/** Filter tokens to return only APP tokens. */
fun <T : OgiriToken> Collection<T>.appTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.APP)

/** Filter tokens to return only SUB tokens. */
fun <T : OgiriToken> Collection<T>.subTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.SUB)

/**
 * Extract client IDs from a collection of APP tokens.
 *
 * @return Set of unique client IDs
 */
fun <T : OgiriToken> Collection<T>.clientIds(): Set<String> = map { it.client }.toSet()

/**
 * Filter tokens by multiple client IDs.
 *
 * @param clientIds The client IDs to filter by
 * @return List of tokens with clients in the given set
 */
fun <T : OgiriToken> Collection<T>.filterByClientIds(clientIds: Set<String>): List<T> = filter {
  it.client in clientIds
}

/**
 * Invert filter - find tokens NOT in the given client IDs.
 *
 * @param clientIds The client IDs to exclude
 * @return List of tokens with clients NOT in the given set
 */
fun <T : OgiriToken> Collection<T>.filterOutClientIds(clientIds: Set<String>): List<T> = filter {
  it.client !in clientIds
}
