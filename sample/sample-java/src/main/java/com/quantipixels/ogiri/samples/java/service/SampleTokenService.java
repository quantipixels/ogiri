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
package com.quantipixels.ogiri.samples.java.service;

import com.quantipixels.ogiri.samples.java.entity.SampleToken;
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties;
import com.quantipixels.ogiri.security.core.IdentifierPolicy;
import com.quantipixels.ogiri.security.spi.OgiriAuditHook;
import com.quantipixels.ogiri.security.spi.OgiriRateLimitHook;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry;
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository;
import com.quantipixels.ogiri.security.tokens.OgiriTokenService;
import com.quantipixels.ogiri.security.tokens.OgiriTokenType;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Sample TokenService implementation for the Java example app.
 *
 * <p>This service demonstrates how users should extend OgiriTokenService and override the
 * tokenFactory() method to instantiate their custom Token class.
 *
 * <p>Since SampleToken extends OgiriBaseTokenEntity, it inherits all fields. The tokenFactory()
 * simply creates a new instance and sets the required properties.
 */
@Service
public class SampleTokenService extends OgiriTokenService<SampleToken> {

  /** Constructor - injects all dependencies needed by TokenService. */
  public SampleTokenService(
      OgiriTokenRepository<SampleToken> tokenRepository,
      PasswordEncoder passwordEncoder,
      OgiriUserDirectory userDirectory,
      IdentifierPolicy identifierPolicy,
      OgiriSubTokenRegistry subTokenRegistry,
      OgiriConfigurationProperties properties,
      ObjectProvider<OgiriAuditHook> auditHookProvider,
      ObjectProvider<OgiriRateLimitHook> rateLimitHookProvider) {
    super(
        tokenRepository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        subTokenRegistry,
        properties,
        auditHookProvider,
        rateLimitHookProvider);
  }

  /**
   * Factory method for creating SampleToken instances.
   *
   * <p>Since SampleToken extends OgiriBaseTokenEntity, all fields are inherited and can be set via
   * setters.
   */
  @Override
  protected SampleToken tokenFactory(
      long userId,
      String client,
      String hashedToken,
      OgiriTokenType tokenType,
      Instant expiry,
      String tokenSubtype,
      String plainTokenValue) {
    SampleToken token = new SampleToken();
    token.setUserId(userId);
    token.setClient(client);
    token.setToken(hashedToken);
    token.setTokenType(tokenType.name());
    token.setExpiryAt(expiry);
    token.setTokenSubtype(tokenSubtype);
    token.setPlainToken(plainTokenValue);
    return token;
  }
}
