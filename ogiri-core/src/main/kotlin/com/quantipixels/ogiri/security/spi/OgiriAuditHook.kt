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

/**
 * Audit hook for security-significant events.
 *
 * Consumers implement this interface to integrate with their logging/SIEM systems (e.g., Splunk,
 * ELK, CloudWatch). The default implementation is a no-op.
 */
interface OgiriAuditHook {
  /**
   * Called after a successful login.
   *
   * @param userId The authenticated user's ID.
   * @param client The client ID of the newly issued token.
   * @param ip The remote IP address, or `null` if unavailable.
   */
  fun onLoginSuccess(userId: Long, client: String, ip: String?) {}

  /**
   * Called when a login attempt fails.
   *
   * @param identifier The email or username supplied in the attempt.
   * @param reason A short machine-readable string describing the failure (e.g., `"user_not_found"`,
   *   `"invalid_password"`).
   * @param ip The remote IP address, or `null` if unavailable.
   */
  fun onLoginFailure(identifier: String, reason: String, ip: String?) {}

  /**
   * Called after an APP token is rotated for a user/client.
   *
   * @param userId The user whose token was rotated.
   * @param client The client ID of the rotated token.
   */
  fun onTokenRotated(userId: Long, client: String) {}

  /**
   * Called after an APP token is explicitly revoked.
   *
   * @param userId The user whose token was revoked.
   * @param client The client ID of the revoked token.
   */
  fun onTokenRevoked(userId: Long, client: String) {}

  /**
   * Called after a new sub-token is issued for a parent client.
   *
   * @param userId The user the sub-token belongs to.
   * @param parentClient The client ID of the parent APP token.
   * @param subTokenName The registration name of the sub-token (e.g., `"device"`, `"chat"`).
   */
  fun onSubTokenCreated(userId: Long, parentClient: String, subTokenName: String) {}
}
