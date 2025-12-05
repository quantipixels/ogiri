# Configuration Guide

This guide covers Spring Boot configuration properties and auto-configuration options for ogiri security library.

## Configuration Properties

All ogiri configuration properties are prefixed with `ogiri` and organized by category.

### Security Filter Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ogiri.security.register-filter` | boolean | `true` | Automatically register SecurityFilterChain bean for token authentication. Set to `false` if you want to manually wire the filter or provide custom security configuration. |

### Authentication & Token Behavior

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ogiri.auth.max-clients` | long | `24` | Maximum number of active APP tokens per user. Oldest tokens are revoked when this limit is exceeded. |
| `ogiri.auth.batch-grace-seconds` | long | `5` | Grace period (seconds) for detecting batch requests. Requests within this window don't trigger token rotation, only update `lastUsedAt`. Prevents token thrashing from rapid multi-request batches. |
| `ogiri.auth.token-lifespan-days` | long | `14` | Default token lifetime in days. Tokens expire `token-lifespan-days` after creation. Can be overridden per sub-token. |

### Token Rotation Policy

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ogiri.auth.rotate-on-write-only` | boolean | `false` | Only rotate tokens on mutating HTTP requests (POST, PUT, DELETE, PATCH). GET requests update `lastUsedAt` but don't trigger rotation. Use when you want to reduce token rotation overhead. |
| `ogiri.auth.rotate-stale-seconds` | long | `0` | Force token rotation if token exceeds this age (seconds), regardless of request batching. Set to `0` (default) to disable staleness-based rotation and rely only on batch window logic. Use `3600` (1 hour) to force daily token refresh. |

### Scheduled Token Cleanup

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ogiri.cleanup.enabled` | boolean | `true` | Enable scheduled `TokenCleanupJob` to delete expired tokens from database. |
| `ogiri.cleanup.cron` | string | `0 0 * * * *` | Cron expression for cleanup job. Default runs daily at midnight. |

## Configuration Examples

### Basic Setup (application.yml)

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 24
    batch-grace-seconds: 5
    token-lifespan-days: 14
    rotate-on-write-only: false
    rotate-stale-seconds: 0
  cleanup:
    enabled: true
    cron: "0 0 * * * *"  # Daily at midnight
```

### Properties File (application.properties)

```properties
ogiri.security.register-filter=true
ogiri.auth.max-clients=24
ogiri.auth.batch-grace-seconds=5
ogiri.auth.token-lifespan-days=14
ogiri.auth.rotate-on-write-only=false
ogiri.auth.rotate-stale-seconds=0
ogiri.cleanup.enabled=true
ogiri.cleanup.cron=0 0 * * * *
```

### High-Security Configuration

For applications requiring frequent token rotation and strict client limits:

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 5                # Only 5 active sessions per user
    batch-grace-seconds: 1        # Minimal grace period
    token-lifespan-days: 7        # Shorter token lifetime
    rotate-on-write-only: false   # Rotate on all requests
    rotate-stale-seconds: 3600    # Force rotation every hour
  cleanup:
    enabled: true
    cron: "0 0 * * * *"
```

### Performance-Optimized Configuration

For high-traffic applications prioritizing throughput:

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 50               # More concurrent sessions
    batch-grace-seconds: 30       # Longer batch window
    token-lifespan-days: 30       # Longer token lifetime
    rotate-on-write-only: true    # Only rotate on POST/PUT/DELETE
    rotate-stale-seconds: 0       # Disable staleness-based rotation
  cleanup:
    enabled: true
    cron: "0 0 * * * *"
```

## Environment-Specific Configuration

### Development (application-dev.yml)

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 100              # Lenient for testing multiple scenarios
    batch-grace-seconds: 60       # Long grace period for local testing
    token-lifespan-days: 30
    rotate-on-write-only: false
    rotate-stale-seconds: 0
  cleanup:
    enabled: false                # Disabled for dev to keep test tokens
```

### Production (application-prod.yml)

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 10               # Strict limit
    batch-grace-seconds: 5        # Conservative batch window
    token-lifespan-days: 7        # Short-lived tokens
    rotate-on-write-only: false
    rotate-stale-seconds: 3600    # Force hourly rotation
  cleanup:
    enabled: true
    cron: "0 */6 * * * *"         # Every 6 hours
```

## Spring Boot Auto-Configuration

### Conditional Bean Registration

The library uses Spring Boot's `@ConditionalOnMissingBean` to allow customization:

```kotlin
// Auto-configured if not already defined
@ConditionalOnMissingBean
@Bean
fun tokenService(
  tokenRepository: TokenRepository<Token>,
  tokenUserDirectory: TokenUserDirectory,
  @Value("\${ogiri.auth.max-clients:24}") maxClients: Long,
  @Value("\${ogiri.auth.batch-grace-seconds:5}") batchGraceSeconds: Long,
  @Value("\${ogiri.auth.token-lifespan-days:14}") tokenLifespanDays: Long
): TokenService<Token> = TokenService(
  tokenRepository,
  tokenUserDirectory,
  maxClients,
  batchGraceSeconds,
  tokenLifespanDays
)
```

### Custom TokenService Bean

To provide your own `TokenService` implementation with custom logic:

```kotlin
@Configuration
class CustomSecurityConfig {
  @Bean
  fun tokenService(
    tokenRepository: TokenRepository<MyToken>,
    tokenUserDirectory: TokenUserDirectory
  ): TokenService<MyToken> = MyCustomTokenService(
    tokenRepository,
    tokenUserDirectory,
    maxClients = 10,
    batchGraceSeconds = 30,
    tokenLifespanDays = 7
  )
}
```

### Custom SecurityFilterChain

To disable auto-configuration and wire your own filter chain:

```kotlin
@Configuration
class CustomSecurityConfig {
  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    return http
      .authorizeRequests { it.anyRequest().authenticated() }
      .addFilter(OgiriTokenAuthenticationFilter(tokenService, authBypassDecider))
      .build()
  }
}
```

Disable auto-configuration with:

```yaml
ogiri:
  security:
    register-filter: false
```

## Configuration Validation

### At Startup

Spring Boot validates configuration properties at startup. Invalid values result in clear error messages:

```
Binding validation errors on 'ogiri':
    Field error in object 'ogiri' on field 'auth.batchGraceSeconds':
    rejected value [-1]; codes ...
    default message 'must be greater than or equal to 0'
```

### Common Configuration Mistakes

| Issue | Solution |
|-------|----------|
| Token expires immediately | Increase `token-lifespan-days` (default: 14) |
| Tokens rotate too frequently | Increase `batch-grace-seconds` or disable `rotate-stale-seconds` |
| Too many active tokens | Decrease `max-clients` limit |
| Tests fail with token mismatch | Disable cleanup in test profile: `ogiri.cleanup.enabled=false` |

## References

- **[AUTHENTICATION.md](./AUTHENTICATION.md)** – Detailed token rotation and lifecycle documentation
- **[DATABASE.md](./DATABASE.md)** – Token storage and persistence layer
- **[README.md](../README.md)** – Quick start and feature overview
