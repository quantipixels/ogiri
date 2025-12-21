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
package com.quantipixels.ogiri.security.tokens

import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class OgiriTokenCleanupJob(
    private val tokenServiceResolver: OgiriTokenServiceResolver,
) {
  private val logger = LoggerFactory.getLogger(OgiriTokenCleanupJob::class.java)

  /**
   * Removes expired user tokens from the resolved token service.
   *
   * If any tokens are removed, logs the count at info level.
   */
  @Scheduled(fixedDelayString = "\${ogiri.cleanup.interval-ms:21600000}")
  fun cleanupExpiredTokens() {
    val deleted = tokenServiceResolver.resolve().cleanupExpiredTokens(Instant.now())
    if (deleted > 0) {
      logger.info("Expired user tokens removed count={}", deleted)
    }
  }
}
