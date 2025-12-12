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
package com.quantipixels.ogiri.security.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Centralized configuration properties for ogiri security library.
 *
 * All ogiri configuration is prefixed with `ogiri.` in application.yml/application.properties.
 * Properties are organized into three nested categories:
 * - security: Filter registration and general security options
 * - auth: Token behavior and rotation policies
 * - cleanup: Scheduled token cleanup job configuration
 *
 * Example application.yml:
 * ```yaml
 * ogiri:
 *   security:
 *     register-filter: true
 *   auth:
 *     max-clients: 24
 *     batch-grace-seconds: 5
 *     token-lifespan-days: 14
 *     rotate-on-write-only: false
 *     rotate-stale-seconds: 0
 *     register-token-service: true
 *   cleanup:
 *     enabled: true
 *     cron: "0 0 * * * *"
 * ```
 */
@Validated
@ConfigurationProperties(prefix = "ogiri")
open class OgiriConfigurationProperties {
  /**
   * Security filter configuration. Controls filter registration and general authentication
   * behavior.
   */
  val security: SecurityProperties = SecurityProperties()

  /**
   * Token authentication and rotation configuration. Controls token lifecycle, rotation policies,
   * and grace periods.
   */
  @field:Valid val auth: AuthProperties = AuthProperties()

  /**
   * Scheduled token cleanup job configuration. Controls deletion of expired tokens from the
   * database.
   */
  val cleanup: CleanupProperties = CleanupProperties()

  /** Security filter configuration properties. */
  open class SecurityProperties {
    /**
     * Enable automatic registration of the ogiri SecurityFilterChain bean.
     *
     * Set to false if you want to manually wire the filter or provide custom security configuration
     * that doesn't use the auto-configuration.
     *
     * Default: true
     */
    var registerFilter: Boolean = true
  }

  /**
   * Token authentication and rotation configuration properties.
   *
   * These properties control token issuance, validation, and rotation behavior. All settings are
   * sourced from application configuration and NOT from TokenService constructor arguments.
   */
  open class AuthProperties {
    /**
     * Enable auto-registration of the default
     * [com.quantipixels.ogiri.security.tokens.OgiriTokenService].
     *
     * Set to false if your application provides its own
     * [com.quantipixels.ogiri.security.tokens.OgiriTokenService] (or you want to fully customize
     * how token services are wired).
     *
     * Default: true
     */
    var registerTokenService: Boolean = true

    /**
     * Maximum number of active APP tokens per user.
     *
     * When a new APP token is created and this limit is reached, the oldest token is revoked to
     * maintain the limit. This prevents token accumulation and provides a mechanism for limiting
     * concurrent sessions.
     *
     * Default: 24 Valid Range: 1 - no upper limit
     *
     * Examples:
     * - High-security: 5 (only 5 concurrent sessions)
     * - Default: 10 (reasonable for multi-device users)
     * - Relaxed: 50 (for testing or high-concurrency scenarios)
     */
    @field:Min(1) var maxClients: Long = 10

    /**
     * Grace period (seconds) for detecting batch requests within a request window.
     *
     * When multiple requests arrive within this window, the TokenService only updates the
     * lastUsedAt timestamp without issuing a new token. This prevents "token thrashing" from rapid
     * consecutive requests (e.g., simultaneous API calls from the same client or parallel
     * image/resource loads).
     *
     * Requests outside this window trigger token rotation and emit new auth headers.
     *
     * Default: 5 Valid Range: 0 - any positive integer
     *
     * Examples:
     * - Conservative: 1 second (rotate frequently)
     * - Default: 5 seconds (balance between rotation and efficiency)
     * - Relaxed: 30 seconds (for high-traffic scenarios)
     * - Development: 60 seconds (minimize rotation during testing)
     *
     * See [OgiriTokenAuthenticationFilter.doFilterInternal] for batch window logic.
     */
    @field:Min(0) var batchGraceSeconds: Long = 5

    /**
     * Default token lifetime in days.
     *
     * New tokens created by TokenService.createNewAuthToken() will expire after this duration. This
     * applies to APP tokens only; sub-tokens can override this by implementing custom expiry logic
     * in OgiriSubTokenRegistration.expiry().
     *
     * Default: 14 Valid Range: 1 - any positive integer
     *
     * Examples:
     * - Short-lived: 7 days (high security, frequent re-auth)
     * - Default: 14 days (balance between security and user experience)
     * - Long-lived: 30 days (minimal re-auth, lower security)
     *
     * Set via application.yml:
     * ```yaml
     * ogiri:
     *   auth:
     *     token-lifespan-days: 7
     * ```
     */
    @field:Min(1) var tokenLifespanDays: Long = 14

    /**
     * Only rotate tokens on mutating HTTP requests (POST, PUT, DELETE, PATCH).
     *
     * When true, token rotation is triggered only for requests that modify server state. GET
     * requests update lastUsedAt but do NOT trigger rotation, reducing rotation overhead for
     * read-heavy workloads.
     *
     * Default: false (rotate on all requests) Valid Values: true, false
     *
     * Use Cases:
     * - Read-heavy APIs: Set to true to reduce rotation overhead
     * - Standard APIs: Keep false for consistent token rotation
     *
     * See [OgiriTokenAuthenticationFilter.rotateTokensIfNeeded] for implementation.
     */
    var rotateOnWriteOnly: Boolean = false

    /**
     * Force token rotation if token exceeds this age (seconds).
     *
     * Regardless of request batching, if a token has been in use for longer than this duration, it
     * will be rotated on the next request. This provides a safeguard against indefinitely
     * long-lived tokens and ensures periodic credential refresh.
     *
     * Default: 0 (disabled, no staleness-based rotation) Valid Range: 0 (disabled) or any positive
     * integer (seconds)
     *
     * Examples:
     * - Disabled: 0 (rely only on batch window logic)
     * - Hourly: 3600 (force rotation every hour)
     * - Daily: 86400 (force rotation every day)
     *
     * See [OgiriTokenAuthenticationFilter.rotateTokensIfNeeded] for implementation.
     */
    var rotateStaleSeconds: Long = 0
  }

  /**
   * Scheduled token cleanup job configuration.
   *
   * The [OgiriTokenCleanupJob] runs periodically to delete expired tokens from the database,
   * preventing accumulation of stale data.
   */
  open class CleanupProperties {
    /**
     * Enable the scheduled OgiriTokenCleanupJob.
     *
     * When true, the cleanup job runs at a fixed interval and deletes all tokens where expiryAt <
     * now(). When false, the cleanup job is not registered or executed.
     *
     * Default: true Valid Values: true, false
     *
     * Use Cases:
     * - Production: true (automatic cleanup)
     * - Testing: false (preserve test tokens for inspection)
     * - Custom cleanup: false (implement your own cleanup logic)
     */
    var enabled: Boolean = true

    /**
     * Interval in milliseconds between cleanup job executions.
     *
     * The cleanup job uses a fixed delay, meaning the next execution starts after the specified
     * interval has elapsed since the previous execution completed.
     *
     * Default: 21600000 (6 hours)
     *
     * Examples:
     * - 21600000 (6 hours) - default, good for most production use cases
     * - 3600000 (1 hour) - for higher-traffic applications
     * - 43200000 (12 hours) - for lower-traffic applications
     *
     * Only used if [enabled] is true.
     */
    var intervalMs: Long = 21600000
  }
}
