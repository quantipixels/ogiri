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
  fun beforeLogin(request: HttpServletRequest, identifier: String) {}
  fun beforeTokenCreation(request: HttpServletRequest, userId: Long) {}
}
