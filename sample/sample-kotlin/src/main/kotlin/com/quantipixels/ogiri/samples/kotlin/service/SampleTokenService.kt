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
package com.quantipixels.ogiri.samples.kotlin.service

import com.quantipixels.ogiri.samples.kotlin.entity.SampleToken
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenType
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Sample TokenService implementation for the Kotlin example app.
 *
 * This service demonstrates how users should extend OgiriTokenService and override the
 * tokenFactory() method to instantiate their custom Token class.
 *
 * Since SampleToken extends OgiriBaseTokenEntity, it inherits all fields. The tokenFactory() simply
 * creates a new instance and sets the required properties.
 *
 * The optional [OgiriAuditHook] and [OgiriRateLimitHook] follow the same injection pattern as
 * [OgiriTokenLookupCache]: add them as nullable constructor parameters and pass via named argument.
 */
@Service
@Profile("!jdbc")
class SampleTokenService(
    tokenRepository: OgiriTokenRepository<SampleToken>,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
    lookupCache: OgiriTokenLookupCache<SampleToken>? = null,
) :
    OgiriTokenService<SampleToken>(
        tokenRepository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        subTokenRegistry,
        properties,
        lookupCache = lookupCache,
    ) {

  /**
   * Factory method for creating SampleToken instances.
   *
   * Since SampleToken extends OgiriBaseTokenEntity, all fields are inherited and can be set via
   * property assignment.
   */
  override fun tokenFactory(
      userId: Long,
      client: String,
      hashedToken: String,
      tokenType: OgiriTokenType,
      expiry: Instant,
      tokenSubtype: String?,
      plainTokenValue: String,
  ): SampleToken =
      SampleToken().apply {
        this.userId = userId
        this.client = client
        this.token = hashedToken
        this.tokenType = tokenType.name
        this.expiryAt = expiry
        this.tokenSubtype = tokenSubtype
        this.plainToken = plainTokenValue
      }
}
