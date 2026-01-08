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
 * Implementations can plug into [OgiriSubTokenRegistry] to declare additional token kinds (e.g.,
 * device-scoped tokens, chat credentials). The contract is intentionally small: name, client id
 * mapping, expiry policy, and whether creation should be forced on every issuance.
 *
 * Implementations may also provide custom validation logic via [validate] for format-specific
 * checks (e.g., service token signature verification, device fingerprint validation).
 */
interface OgiriSubTokenRegistration {
  /** Unique name for the sub-token (e.g., "device", "chat"). */
  val name: String

  /** Whether this sub-token should be created when no explicit list is provided. */
  val includeByDefault: Boolean
    get() = true

  /**
   * Compute the client id for this sub-token based on the parent APP client id.
   *
   * @param parentClientId The parent APP client id used to derive the sub-token's client id.
   * @return The computed client id for the sub-token.
   */
  fun clientIdFor(parentClientId: String): String

  /**
   * Determine the expiry Instant for this sub-token based on the parent APP expiry.
   *
   * The returned expiry will not be later than [parentExpiry].
   *
   * @param parentExpiry The expiry Instant of the parent APP token.
   * @return The expiry Instant for the sub-token (no later than [parentExpiry]).
   */
  fun expiry(parentExpiry: Instant): Instant

  /** Whether issuing this sub-token should always rotate (overwrite). */
  val forceNew: Boolean
    get() = false

  /**
   * Performs additional validation of a sub-token after its hash has been verified.
   *
   * @param plainToken The raw (unhashed) token value to validate.
   * @return `true` if the token passes custom validation, `false` otherwise. Default implementation
   *   returns `true`.
   */
  fun validate(plainToken: String): Boolean = true
}

interface OgiriSubTokenRegistry {
  /**
   * Retrieve the registered sub-token registrations.
   *
   * @return A `List<OgiriSubTokenRegistration>` containing all registrations in registration order.
   */
  fun registrations(): List<OgiriSubTokenRegistration>
}

/**
 * Simple registry implementation backed by a provided list of [OgiriSubTokenRegistration] beans.
 */
class DefaultOgiriSubTokenRegistry(
    private val registrations: List<OgiriSubTokenRegistration> = emptyList(),
) : OgiriSubTokenRegistry {
  /**
   * Provides the list of registered Ogiri sub-token registrations.
   *
   * @return The list of `OgiriSubTokenRegistration` instances held by this registry.
   */
  override fun registrations(): List<OgiriSubTokenRegistration> = registrations
}
