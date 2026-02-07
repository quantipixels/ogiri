# Configuration Reference

All properties prefixed with `ogiri.*`

## Security

```yaml
ogiri:
  security:
    register-filter: true     # Auto-register SecurityFilterChain
```

## Authentication

```yaml
ogiri:
  auth:
    max-clients: 10                    # Max tokens per user
    batch-grace-seconds: 5             # Batch request window
    token-lifespan-days: 14            # Token TTL
    rotate-on-write-only: false        # Only rotate on POST/PUT/DELETE
    rotate-stale-seconds: 3600         # Force rotation threshold (0=disabled)
    max-bearer-token-size: 8192        # DoS protection
```

### Startup Warnings

Logged for:
- `rotate-stale-seconds: 0` (rotation disabled)
- `secure: false` (insecure cookies)
- `http-only: false` (XSS vulnerable)

## Cleanup

```yaml
ogiri:
  cleanup:
    enabled: true              # Enable scheduled cleanup
    interval-ms: 21600000      # 6 hours
    batch-size: 1000           # Deletion batch size
```

## Cookies

```yaml
ogiri:
  cookies:
    enabled: true              # Use cookies for auth headers
    secure: true               # Require HTTPS
    http-only: true            # Prevent JavaScript access
    same-site: Strict          # CSRF protection (Strict|Lax|None)
    path: /                    # Cookie path
```

**On 401**: If `cookies.enabled=true`, auth cookies cleared (maxAge=0) to prevent stale-cookie loops.

## Cache

```yaml
ogiri:
  cache:
    max-size: 10000            # Token equality cache size
    expiry-minutes: 60         # Cache TTL
```

## Property Synchronization

⚠️ Do not rename headers or property names without updating:
- Documentation in `docs/configuration.md`
- Tests
- `OgiriConfigurationProperties` defaults

Keep config keys synchronized with docs.

## Sample Apps (Optional)

Sample apps require **PostgreSQL** at `localhost:5432`.

```bash
# Kotlin sample
./gradlew :sample:sample-kotlin:bootRun

# Java sample
./gradlew :sample:sample-java:bootRun
```

See `sample/README.md` for credentials/setup.

## Version Management

- Version source: `.ogiri-version`
- Override via `RELEASE_VERSION` env or Gradle property
- BOM from Spring Boot 3.5.7
