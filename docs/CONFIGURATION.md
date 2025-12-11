# Configuration

All ogiri properties are prefixed with `ogiri`.

## Properties Reference

### Security Filter

| Property | Default | Description |
|----------|---------|-------------|
| `ogiri.security.register-filter` | `true` | Auto-register SecurityFilterChain |

### Token Behavior

| Property | Default | Description |
|----------|---------|-------------|
| `ogiri.auth.max-clients` | `24` | Max active tokens per user |
| `ogiri.auth.batch-grace-seconds` | `5` | Grace period before rotation |
| `ogiri.auth.token-lifespan-days` | `14` | Token lifetime in days |

### Token Rotation

| Property | Default | Description |
|----------|---------|-------------|
| `ogiri.auth.rotate-on-write-only` | `false` | Only rotate on POST/PUT/DELETE |
| `ogiri.auth.rotate-stale-seconds` | `0` | Force rotation after N seconds (0 = disabled) |

### Token Cleanup

| Property | Default | Description |
|----------|---------|-------------|
| `ogiri.cleanup.enabled` | `true` | Enable scheduled cleanup job |
| `ogiri.cleanup.cron` | `0 0 * * * *` | Cleanup schedule (daily at midnight) |

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
  cleanup:
    enabled: true
```

### High Security

Frequent rotation, short tokens, strict limits:

```yaml
ogiri:
  auth:
    max-clients: 5
    batch-grace-seconds: 1
    token-lifespan-days: 7
    rotate-on-write-only: false
    rotate-stale-seconds: 3600  # Force rotation every hour
  cleanup:
    cron: "0 0 * * * *"
```

### High Performance

Longer tokens, less rotation:

```yaml
ogiri:
  auth:
    max-clients: 50
    batch-grace-seconds: 30
    token-lifespan-days: 30
    rotate-on-write-only: true   # Only rotate on writes
    rotate-stale-seconds: 0      # No forced rotation
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
    enabled: false  # Keep test tokens
```

## Custom Beans

### Custom TokenService

```kotlin
@Configuration
class CustomConfig(private val properties: OgiriConfigurationProperties) {

  @Bean
  fun tokenService(
    tokenRepository: TokenRepository<MyToken>,
    passwordEncoder: PasswordEncoder,
    ogiriUserDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: SubTokenRegistry,
  ): TokenService<MyToken> =
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
ogiri.auth.rotate-on-write-only=false
ogiri.auth.rotate-stale-seconds=0
ogiri.cleanup.enabled=true
ogiri.cleanup.cron=0 0 * * * *
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Token expires immediately | Increase `token-lifespan-days` |
| Tokens rotate too frequently | Increase `batch-grace-seconds` |
| Too many active tokens | Decrease `max-clients` |
| Tests fail with token mismatch | Set `ogiri.cleanup.enabled=false` in test profile |
