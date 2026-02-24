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
package com.quantipixels.ogiri.samples.java.jdbc;

import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties;
import com.quantipixels.ogiri.security.core.IdentifierPolicy;
import com.quantipixels.ogiri.security.spi.OgiriTokenLookupCache;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry;
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository;
import com.quantipixels.ogiri.security.tokens.OgiriTokenService;
import com.quantipixels.ogiri.security.tokens.OgiriTokenType;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Token service for the JDBC-backed Java sample.
 *
 * <p>Active only when the "jdbc" Spring profile is enabled. The companion JPA-backed {@link
 * com.quantipixels.ogiri.samples.java.service.SampleTokenService} is excluded via
 * {@code @Profile("!jdbc")}, so only one OgiriTokenService bean exists at runtime.
 *
 * <p>Run with: {@code --spring.profiles.active=jdbc}
 */
@Service
@Profile("jdbc")
public class JdbcSampleTokenService extends OgiriTokenService<JdbcSampleToken> {

  public JdbcSampleTokenService(
      OgiriTokenRepository<JdbcSampleToken> tokenRepository,
      PasswordEncoder passwordEncoder,
      OgiriUserDirectory userDirectory,
      IdentifierPolicy identifierPolicy,
      OgiriSubTokenRegistry subTokenRegistry,
      OgiriConfigurationProperties properties,
      @Nullable OgiriTokenLookupCache<JdbcSampleToken> lookupCache) {
    super(
        tokenRepository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        subTokenRegistry,
        properties,
        /* auditHook */ null,
        /* rateLimitHook */ null,
        lookupCache);
  }

  @Override
  protected JdbcSampleToken tokenFactory(
      long userId,
      String client,
      String hashedToken,
      OgiriTokenType tokenType,
      Instant expiry,
      String tokenSubtype,
      String plainTokenValue) {
    JdbcSampleToken token = new JdbcSampleToken();
    token.setUserId(userId);
    token.setClient(client);
    token.setToken(hashedToken);
    token.setTokenType(tokenType.getLabel());
    token.setExpiryAt(expiry);
    token.setTokenSubtype(tokenSubtype);
    token.setPlainToken(plainTokenValue);
    return token;
  }
}
