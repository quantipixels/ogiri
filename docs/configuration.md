# Configuration

All ogiri properties are prefixed with `ogiri`.

## Properties Reference

### Security Filter

| Property                         | Default | Description                       |
| -------------------------------- | ------- | --------------------------------- |
| `ogiri.security.register-filter` | `true`  | Auto-register SecurityFilterChain |

### Token Behavior

| Property                            | Default | Description                                     |
| ----------------------------------- | ------- | ----------------------------------------------- |
| `ogiri.auth.max-clients`            | `24`    | Max active tokens per user                      |
| `ogiri.auth.batch-grace-seconds`    | `5`     | Grace period before rotation                    |
| `ogiri.auth.token-lifespan-days`    | `14`    | Token lifetime in days                          |
| `ogiri.auth.max-bearer-token-size`  | `8192`  | Max bearer token size in bytes (DoS protection) |
| `ogiri.auth.register-token-service` | `true`  | Auto-register default OgiriTokenService         |

### Token Rotation

| Property                          | Default | Description                                   |
| --------------------------------- | ------- | --------------------------------------------- |
| `ogiri.auth.rotate-on-write-only` | `false` | Only rotate on POST/PUT/DELETE                |
| `ogiri.auth.rotate-stale-seconds` | `3600`  | Force rotation after N seconds (0 = disabled) |

### Token Cleanup

| Property                    | Default    | Description                                           |
| --------------------------- | ---------- | ----------------------------------------------------- |
| `ogiri.cleanup.enabled`     | `true`     | Enable scheduled cleanup job                          |
| `ogiri.cleanup.interval-ms` | `21600000` | Cleanup interval in milliseconds (default: 6 hours)   |
| `ogiri.cleanup.batch-size`  | `1000`     | Tokens deleted per batch (large dataset optimization) |

### Cookie Configuration

| Property                  | Default  | Description                          |
| ------------------------- | -------- | ------------------------------------ |
| `ogiri.cookies.enabled`   | `true`   | Enable auth cookies                  |
| `ogiri.cookies.secure`    | `true`   | Require HTTPS (WARN if false)        |
| `ogiri.cookies.http-only` | `true`   | Prevent JS access (WARN if false)    |
| `ogiri.cookies.same-site` | `Strict` | SameSite attribute (Strict/Lax/None) |
| `ogiri.cookies.path`      | `"/"`    | Cookie path                          |

## Configuration Examples

### Basic Setup

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 24
    batch-grace-seconds: 5
    token-lifespan-days: 14
    max-bearer-token-size: 8192
    register-token-service: true
  cleanup:
    enabled: true
    interval-ms: 21600000 # 6 hours
    batch-size: 1000
  cookies:
    enabled: true
    secure: true
    http-only: true
    same-site: Strict
    path: "/"
```

### High Security

Frequent rotation, short tokens, strict limits:

```yaml
ogiri:
  auth:
    max-clients: 5
    batch-grace-seconds: 1
    token-lifespan-days: 7
    max-bearer-token-size: 4096 # Stricter limit
    rotate-on-write-only: false
    rotate-stale-seconds: 3600 # Force rotation every hour
  cleanup:
    interval-ms: 3600000 # 1 hour
    batch-size: 500
  cookies:
    enabled: true
    secure: true
    http-only: true
    same-site: Strict
```

### High Performance

Longer tokens, less rotation:

```yaml
ogiri:
  auth:
    max-clients: 50
    batch-grace-seconds: 30
    token-lifespan-days: 30
    rotate-on-write-only: true # Only rotate on writes
    rotate-stale-seconds: 0 # No forced rotation
```

### Development

Lenient settings for testing:

```yaml
ogiri:
  auth:
    max-clients: 100
    batch-grace-seconds: 60
    token-lifespan-days: 30
  cleanup:
    enabled: false # Keep test tokens
  cookies:
    secure: false # Allow HTTP in development
```

## Startup Warnings

The library logs warnings at startup for potentially insecure configurations:

| Configuration                       | Warning                                                |
| ----------------------------------- | ------------------------------------------------------ |
| `ogiri.auth.rotate-stale-seconds=0` | Token rotation disabled; consider using default (3600) |
| `ogiri.cookies.secure=false`        | Enable for HTTPS deployments                           |
| `ogiri.cookies.http-only=false`     | Enable to prevent XSS cookie theft                     |

These warnings are informational and do not prevent the application from starting. They help identify security misconfigurations in production environments.

## Custom Beans

### Custom OgiriTokenService

If you provide your own `OgiriTokenService`, Ògiri will not create its default token service.

If you intentionally have multiple `OgiriTokenService` beans, mark exactly one as `@Primary` or
inject by `@Qualifier` to avoid ambiguity.

```kotlin
@Configuration
class CustomConfig(private val properties: OgiriConfigurationProperties) {

  @Bean
  fun tokenService(
    tokenRepository: OgiriTokenRepository<MyToken>,
    passwordEncoder: PasswordEncoder,
    ogiriUserDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
  ): OgiriTokenService<MyToken> =
    MyCustomTokenService(
      tokenRepository,
      passwordEncoder,
      ogiriUserDirectory,
      identifierPolicy,
      subTokenRegistry,
      properties.auth,
    )
}
```

### Custom SecurityFilterChain

Disable auto-configuration:

```yaml
ogiri:
  security:
    register-filter: false
```

Then provide your own:

```kotlin
@Configuration
class SecurityConfig {

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    return http
      .authorizeRequests { it.anyRequest().authenticated() }
      .addFilter(OgiriTokenAuthenticationFilter(tokenService, authBypassDecider))
      .build()
  }
}
```

## Properties File Format

```properties
ogiri.security.register-filter=true
ogiri.auth.max-clients=24
ogiri.auth.batch-grace-seconds=5
ogiri.auth.token-lifespan-days=14
ogiri.auth.max-bearer-token-size=8192
ogiri.auth.rotate-on-write-only=false
ogiri.auth.rotate-stale-seconds=0
ogiri.auth.register-token-service=true
ogiri.cleanup.enabled=true
ogiri.cleanup.interval-ms=21600000
ogiri.cleanup.batch-size=1000
ogiri.cookies.enabled=true
ogiri.cookies.secure=true
ogiri.cookies.http-only=true
ogiri.cookies.same-site=Strict
ogiri.cookies.path=/
```

## Troubleshooting

| Issue                          | Solution                                          |
| ------------------------------ | ------------------------------------------------- |
| Token expires immediately      | Increase `token-lifespan-days`                    |
| Tokens rotate too frequently   | Increase `batch-grace-seconds`                    |
| Too many active tokens         | Decrease `max-clients`                            |
| Tests fail with token mismatch | Set `ogiri.cleanup.enabled=false` in test profile |
