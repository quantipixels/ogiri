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
package com.quantipixels.ogiri.jdbc

import com.quantipixels.ogiri.security.tokens.OgiriBaseToken
import com.quantipixels.ogiri.security.tokens.OgiriTokenType
import java.time.Instant

/**
 * Base JDBC token row with all required token fields as constructor parameters.
 *
 * Extend this class, add your custom fields, and implement [OgiriJdbcTokenRepository] to provide a
 * [org.springframework.jdbc.core.RowMapper] that maps your table columns to the row class.
 *
 * Example:
 * ```kotlin
 * class MyTokenRow(
 *     id: Long = 0,
 *     userId: Long = 0,
 *     client: String = "",
 *     token: String = "",
 *     expiryAt: Instant = Instant.now(),
 *     val tenantId: String? = null,
 * ) : OgiriBaseTokenRow(id, userId, client, token, expiryAt = expiryAt)
 * ```
 */
open class OgiriBaseTokenRow(
    override var id: Long = 0,
    override var userId: Long = 0,
    override var client: String = "",
    override var token: String = "",
    override var tokenType: String = OgiriTokenType.APP.label,
    override var expiryAt: Instant = Instant.now(),
    override var createdAt: Instant = Instant.now(),
    override var updatedAt: Instant = Instant.now(),
    override var tokenUpdatedAt: Instant = Instant.now(),
) : OgiriBaseToken()
