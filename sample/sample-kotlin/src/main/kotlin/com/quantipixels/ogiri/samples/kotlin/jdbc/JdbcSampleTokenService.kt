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
package com.quantipixels.ogiri.samples.kotlin.jdbc

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties
import com.quantipixels.ogiri.security.core.IdentifierPolicy
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenType
import java.time.Instant
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * Token service for the JDBC-backed sample.
 *
 * Active only when the "jdbc" Spring profile is enabled. The companion JPA-backed
 * [SampleTokenService] is excluded via @Profile("!jdbc"), so only one OgiriTokenService bean exists
 * at runtime.
 *
 * Optional extension points ([com.quantipixels.ogiri.security.spi.OgiriAuditHook],
 * [com.quantipixels.ogiri.security.spi.OgiriRateLimitHook],
 * [com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache]) are wired automatically by the ogiri
 * auto-configuration via setter injection when the corresponding beans are present.
 *
 * Run with: --spring.profiles.active=jdbc
 */
@Service
@Profile("jdbc")
class JdbcSampleTokenService(
    tokenRepository: JdbcSampleTokenRepository,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
) :
    OgiriTokenService<JdbcSampleToken>(
        tokenRepository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        subTokenRegistry,
        properties,
    ) {

  override fun tokenFactory(
      userId: Long,
      client: String,
      hashedToken: String,
      tokenType: OgiriTokenType,
      expiry: Instant,
      tokenSubtype: String?,
      plainTokenValue: String,
  ): JdbcSampleToken =
      JdbcSampleToken().apply {
        this.userId = userId
        this.client = client
        this.token = hashedToken
        this.tokenType = tokenType.label
        this.expiryAt = expiry
        this.tokenSubtype = tokenSubtype
        this.plainToken = plainTokenValue
      }
}
