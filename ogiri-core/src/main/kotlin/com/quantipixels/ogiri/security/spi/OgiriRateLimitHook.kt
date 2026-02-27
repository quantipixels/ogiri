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
package com.quantipixels.ogiri.security.spi

import jakarta.servlet.http.HttpServletRequest

/**
 * Rate limiting hook for authentication endpoints.
 *
 * Consumers implement this to enforce rate limits using their preferred strategy (e.g., Bucket4j,
 * Redis sliding window, in-memory token bucket). The default implementation allows all requests (no
 * rate limiting).
 *
 * Throw [com.quantipixels.ogiri.security.core.SecurityServiceException] with code
 * "error.auth.rate_limited" to reject the request.
 */
interface OgiriRateLimitHook {
  /**
   * Called before a login attempt is processed.
   *
   * @param request The current HTTP request (for IP, headers, etc.).
   * @param identifier The email or username provided by the caller.
   * @throws [com.quantipixels.ogiri.security.core.SecurityServiceException] with code
   *   `"error.auth.rate_limited"` to reject the request.
   */
  fun beforeLogin(request: HttpServletRequest, identifier: String) {}

  /**
   * Called before a new token is issued for a user.
   *
   * @param request The current HTTP request.
   * @param userId The ID of the user requesting a new token.
   * @throws [com.quantipixels.ogiri.security.core.SecurityServiceException] with code
   *   `"error.auth.rate_limited"` to reject the request.
   */
  fun beforeTokenCreation(request: HttpServletRequest, userId: Long) {}

  /**
   * Called before an existing sub-token is renewed for a user.
   *
   * Distinct from [beforeTokenCreation]: renewal replaces an existing sub-token rather than
   * creating a fresh session. Implement to enforce sub-token rotation rate limits independently of
   * APP token creation limits.
   *
   * @param request The current HTTP request (for IP, headers, etc.).
   * @param userId The ID of the user requesting the sub-token renewal.
   * @throws [com.quantipixels.ogiri.security.core.SecurityServiceException] with code
   *   `"error.auth.rate_limited"` to reject the request.
   */
  fun beforeSubTokenRenewal(request: HttpServletRequest, userId: Long) {}
}
