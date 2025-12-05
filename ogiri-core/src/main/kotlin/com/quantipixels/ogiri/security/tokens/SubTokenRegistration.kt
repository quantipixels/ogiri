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

import java.time.Instant

/**
 * Describes a sub-token that should be issued alongside an APP token.
 *
 * Implementations can plug into [SubTokenRegistry] to declare additional token kinds (e.g.,
 * device-scoped tokens, chat credentials). The contract is intentionally small: name, client id
 * mapping, expiry policy, and whether creation should be forced on every issuance.
 */
interface SubTokenRegistration {
  /** Unique name for the sub-token (e.g., "device", "chat"). */
  val name: String

  /** Whether this sub-token should be created when no explicit list is provided. */
  val includeByDefault: Boolean get() = true

  /** Compute the client id for this sub-token given the parent client id. */
  fun clientIdFor(parentClientId: String): String

  /**
   * Compute expiry for the sub-token given the parent APP expiry. Return the desired expiry
   * (typically min(parentExpiry, ttl)).
   */
  fun expiry(parentExpiry: Instant): Instant

  /** Whether issuing this sub-token should always rotate (overwrite). */
  val forceNew: Boolean get() = false
}

interface SubTokenRegistry {
  fun registrations(): List<SubTokenRegistration>
}

/** Simple registry implementation backed by a provided list of [SubTokenRegistration] beans. */
class DefaultSubTokenRegistry(
  private val registrations: List<SubTokenRegistration> = emptyList(),
) : SubTokenRegistry {
  override fun registrations(): List<SubTokenRegistration> = registrations
}
