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
package com.quantipixels.ogiri.security

import com.quantipixels.ogiri.security.tokens.OgiriToken
import java.time.Instant

/** Minimal [OgiriToken] implementation for cache and service unit tests. */
data class OgiriStubToken(
    override var id: Long = 1L,
    override var userId: Long = 1L,
    override var client: String = "client",
    override var token: String = "hash",
    override var tokenType: String = "APP",
    override var expiryAt: Instant = Instant.now().plusSeconds(3600),
    override var createdAt: Instant = Instant.now(),
    override var updatedAt: Instant = Instant.now(),
    override var tokenUpdatedAt: Instant = Instant.now(),
    override var tokenSubtype: String? = null,
    override var lastToken: String? = null,
    override var previousToken: String? = null,
    override var lastUsedAt: Instant? = null,
    override var plainToken: String? = null,
) : OgiriToken
