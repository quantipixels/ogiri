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
  fun onLoginSuccess(userId: Long, client: String, ip: String?) {}
  fun onLoginFailure(identifier: String, reason: String, ip: String?) {}
  fun onTokenRotated(userId: Long, client: String) {}
  fun onTokenRevoked(userId: Long, client: String) {}
  fun onSubTokenCreated(userId: Long, parentClient: String, subTokenName: String) {}
}
