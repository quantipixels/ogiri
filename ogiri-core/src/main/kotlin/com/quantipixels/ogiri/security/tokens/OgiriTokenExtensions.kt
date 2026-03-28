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

/** Filter the collection to tokens that match the specified OgiriTokenType. */
fun <T : OgiriToken> Collection<T>.filterByOgiriTokenType(type: OgiriTokenType): List<T> = filter {
  OgiriTokenType.of(it.tokenType) == type
}

/** Filters the collection to tokens of type APP. */
fun <T : OgiriToken> Collection<T>.appTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.APP)

/** Return tokens whose OgiriTokenType is SUB. */
fun <T : OgiriToken> Collection<T>.subTokens(): List<T> = filterByOgiriTokenType(OgiriTokenType.SUB)

/** Collects unique client IDs from the tokens in this collection. */
fun <T : OgiriToken> Collection<T>.clientIds(): Set<String> = map { it.client }.toSet()

/** Filters tokens whose `client` value is contained in the provided set. */
fun <T : OgiriToken> Collection<T>.filterByClientIds(clientIds: Set<String>): List<T> = filter {
  it.client in clientIds
}

/** Excludes tokens whose client is in the provided set. */
fun <T : OgiriToken> Collection<T>.filterOutClientIds(clientIds: Set<String>): List<T> = filter {
  it.client !in clientIds
}
