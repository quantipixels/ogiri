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
 * Filter the collection to tokens that match the specified OgiriTokenType.
 *
 * @param type The OgiriTokenType to keep.
 * @return A list containing only tokens whose `tokenType` resolves to `type`.
 */
fun <T : OgiriToken> Collection<T>.filterByOgiriTokenType(type: OgiriTokenType): List<T> = filter {
  OgiriTokenType.of(it.tokenType) == type
}

/**
 * Filters the collection to tokens of type APP.
 *
 * @return A list of tokens whose token type corresponds to `OgiriTokenType.APP`.
 */
fun <T : OgiriToken> Collection<T>.appTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.APP)

/**
 * Return tokens whose OgiriTokenType is SUB.
 *
 * @return A list containing only tokens with type SUB.
 */
fun <T : OgiriToken> Collection<T>.subTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.SUB)

/**
 * Collects unique client IDs from the tokens in this collection.
 *
 * @return A set of client ID strings present in the collection.
 */
fun <T : OgiriToken> Collection<T>.clientIds(): Set<String> = map { it.client }.toSet()

/**
 * Filters tokens whose `client` value is contained in the provided set.
 *
 * @param clientIds Set of client IDs to include.
 * @return List of tokens with a `client` present in `clientIds`.
 */
fun <T : OgiriToken> Collection<T>.filterByClientIds(clientIds: Set<String>): List<T> = filter {
  it.client in clientIds
}

/**
 * Exclude tokens whose client is in the provided set.
 *
 * @param clientIds Set of client IDs to exclude.
 * @return List of tokens whose `client` is not contained in `clientIds`.
 */
fun <T : OgiriToken> Collection<T>.filterOutClientIds(clientIds: Set<String>): List<T> = filter {
  it.client !in clientIds
}
