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
import com.quantipixels.ogiri.samples.java.repository.SampleTokenRepositoryAdapter;
import com.quantipixels.ogiri.security.config.OgiriConfigurationProperties;
import com.quantipixels.ogiri.security.core.IdentifierPolicy;
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory;
import com.quantipixels.ogiri.security.tokens.OgiriSubTokenRegistry;
import com.quantipixels.ogiri.security.tokens.OgiriTokenService;
import com.quantipixels.ogiri.security.tokens.OgiriTokenType;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Sample TokenService implementation for the Java example app.
 *
 * <p>This service demonstrates how users should extend TokenService and override the tokenFactory()
 * method to instantiate their custom Token class.
 *
 * <p>In this case, we're using SampleToken (a JPA entity) as the implementation. Other users might
 * use JdbcToken (JDBC), MongoToken (MongoDB), or any other custom Token class extending BaseToken.
 *
 * <p>The tokenFactory() method is called by TokenService when creating new tokens during
 * authentication, token rotation, and sub-token generation.
 */
@Service
@Primary
public class SampleTokenService extends OgiriTokenService<SampleToken> {

  /** Constructor - injects all dependencies needed by TokenService. */
  public SampleTokenService(
      SampleTokenRepositoryAdapter tokenRepository,
      PasswordEncoder passwordEncoder,
      OgiriUserDirectory userDirectory,
      IdentifierPolicy identifierPolicy,
      OgiriSubTokenRegistry subTokenRegistry,
      OgiriConfigurationProperties properties) {
    super(
        tokenRepository,
        passwordEncoder,
        userDirectory,
        identifierPolicy,
        subTokenRegistry,
        properties);
  }

  /**
   * Factory method for creating SampleToken instances.
   *
   * <p>This is called by TokenService whenever a new token needs to be created: -
   * createNewAuthToken() - Creates primary APP token - createOrUpdateToken() - Creates or updates
   * any token (APP or SUB) - issueSubTokens() - Creates sub-tokens (device, chat, etc.)
   *
   * <p>The implementation simply constructs a SampleToken entity with all the required fields.
   * Since SampleToken is a JPA entity, when saved to the repository, Hibernate will handle
   * insert/update with auto-generated IDs and timestamps.
   *
   * @param userId The user ID
   * @param client The client/application identifier
   * @param hashedToken The bcrypt-hashed token value (never plaintext)
   * @param tokenType The token type (APP for primary, SUB for sub-tokens)
   * @param expiry The expiration time for this token
   * @param tokenSubtype Optional sub-token type (e.g., "device", "chat")
   * @param plainTokenValue The plain (unhashed) token - only in-memory, never persisted
   * @return A new SampleToken configured with all parameters
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
    // Create new token with all required fields
    SampleToken token = new SampleToken(userId, client, hashedToken, expiry);

    // Set token type (convert enum to string: "APP" or "SUB")
    token.setTokenType(tokenType.name());

    // Set optional sub-token type
    if (tokenSubtype != null) {
      token.setTokenSubtype(tokenSubtype);
    }

    // Set the plain token for return to client (in-memory only, never persisted)
    token.setPlainToken(plainTokenValue);

    return token;
  }
}
