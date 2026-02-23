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
| `ogiri.auth.max-clients`            | `10`    | Max active tokens per user                      |
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

### BCrypt Comparison Cache

Caches the result of BCrypt comparisons to avoid repeated hashing for the same token.

| Property                     | Default | Description                  |
| ---------------------------- | ------- | ---------------------------- |
| `ogiri.cache.max-size`       | `10000` | Max cached token comparisons |
| `ogiri.cache.expiry-minutes` | `60`    | Cache entry TTL in minutes   |

### Token Lookup Cache

Caches full token entities to eliminate repeated DB reads on every authenticated request.
Disabled by default. Requires `ogiri-caffeine` or `ogiri-redis` on the classpath.

| Property                      | Default | Description                                          |
| ----------------------------- | ------- | ---------------------------------------------------- |
| `ogiri.lookup.type`           | (none)  | `caffeine` or `redis` — absent means no lookup cache |
| `ogiri.lookup.max-size`       | `10000` | Max cached entities (Caffeine only)                  |
| `ogiri.lookup.expiry-minutes` | `5`     | Cache entry TTL in minutes (both Caffeine and Redis) |

## Token Lookup Cache

Every authenticated request calls `getByUserIdAndClient()` — a DB read — to load the token entity.
For high-traffic apps with polling endpoints, this adds up. The token lookup cache eliminates that
read for the same user/client within the configured TTL window.

### Choosing a Backend

| Module           | Property value   | Best for                                         |
| ---------------- | ---------------- | ------------------------------------------------ |
| `ogiri-caffeine` | `type: caffeine` | Single-instance deployments, zero infrastructure |
| `ogiri-redis`    | `type: redis`    | Multi-instance / containerised deployments       |
| (neither)        | (absent)         | Default: every request hits the database         |

!!! warning "Multi-instance deployments"
Caffeine is **per-JVM**. If you run multiple application instances, token revocations
on one node are not visible to others until the cache entry expires. Use `ogiri-redis`
when running more than one instance.

### ogiri-caffeine

Add the dependency (Caffeine is included, no extra peer dep needed):

=== "Gradle (Kotlin DSL)"
`kotlin
    implementation("com.quantipixels.ogiri:ogiri-caffeine:{{ config.extra.ogiri_version }}")
    `

=== "Gradle (Groovy)"
`groovy
    implementation 'com.quantipixels.ogiri:ogiri-caffeine:{{ config.extra.ogiri_version }}'
    `

=== "Maven"
`xml
    <dependency>
      <groupId>com.quantipixels.ogiri</groupId>
      <artifactId>ogiri-caffeine</artifactId>
      <version>{{ config.extra.ogiri_version }}</version>
    </dependency>
    `

Activate in `application.yml`:

```yaml
ogiri:
  lookup:
    type: caffeine
    max-size: 10000
    expiry-minutes: 5
```

### ogiri-redis

Add both the Ogiri Redis module **and** the Spring Data Redis starter (peer dependency):

=== "Gradle (Kotlin DSL)"
`kotlin
    implementation("com.quantipixels.ogiri:ogiri-redis:{{ config.extra.ogiri_version }}")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    `

=== "Gradle (Groovy)"
`groovy
    implementation 'com.quantipixels.ogiri:ogiri-redis:{{ config.extra.ogiri_version }}'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    `

=== "Maven"
`xml
    <dependency>
      <groupId>com.quantipixels.ogiri</groupId>
      <artifactId>ogiri-redis</artifactId>
      <version>{{ config.extra.ogiri_version }}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    `

Activate in `application.yml` (your existing `spring.data.redis.*` config is reused automatically):

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

ogiri:
  lookup:
    type: redis
    expiry-minutes: 5
```

### Custom Cache

Provide your own `OgiriTokenLookupCache<T>` bean and neither autoconfiguration activates:

```kotlin
@Component
class MyCustomTokenCache : OgiriTokenLookupCache<MyToken> {
  override fun get(userId: Long, client: String): MyToken? = TODO()
  override fun put(userId: Long, client: String, token: MyToken) = TODO()
  override fun evict(userId: Long, client: String) = TODO()
  override fun evictAll(userId: Long) = TODO()
}
```

No `ogiri.lookup.type` property is required when supplying a custom bean.

## Configuration Examples

### Basic Setup

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 10
    batch-grace-seconds: 5
    token-lifespan-days: 14
    max-bearer-token-size: 8192
    register-token-service: true
  cleanup:
    enabled: true
    interval-ms: 21600000 # 6 hours
    batch-size: 1000
  cache:
    max-size: 10000
    expiry-minutes: 60
  # lookup.type is absent by default — no entity cache
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

| Configuration                          | Warning                                                |
| -------------------------------------- | ------------------------------------------------------ |
| `ogiri.auth.rotate-stale-seconds=3600` | Token rotation disabled; consider using default (3600) |
| `ogiri.cookies.secure=false`           | Enable for HTTPS deployments                           |
| `ogiri.cookies.http-only=false`        | Enable to prevent XSS cookie theft                     |

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
    auditHookProvider: ObjectProvider<OgiriAuditHook>,
    rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
  ): OgiriTokenService<MyToken> =
    MyCustomTokenService(
      tokenRepository,
      passwordEncoder,
      ogiriUserDirectory,
      identifierPolicy,
      subTokenRegistry,
      properties,
      auditHookProvider,
      rateLimitHookProvider,
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
ogiri.auth.max-clients=10
ogiri.auth.batch-grace-seconds=5
ogiri.auth.token-lifespan-days=14
ogiri.auth.max-bearer-token-size=8192
ogiri.auth.rotate-on-write-only=false
ogiri.auth.rotate-stale-seconds=3600
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
