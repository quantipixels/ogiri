# Ogiri Security - Claude Code Instructions

## Project Overview

Ogiri is a reusable Spring Boot security library written in Kotlin that provides token-based authentication components. It's designed as a pluggable, database-agnostic security framework for Spring Boot 3.5+ applications running on Java 17+.

**Organization:** Quantipixels (com.quantipixels.ogiri)
**License:** Apache 2.0

## Architecture

```
ogiri-core/src/main/kotlin/com/quantipixels/ogiri/security/
├── config/       # Spring Boot auto-configuration & properties
├── core/         # Auth headers, identifiers, JSON codec
├── tokens/       # Token service, repository, sub-tokens
├── web/          # Authentication filter, entry point
├── spi/          # Service Provider Interfaces (OgiriUser, OgiriUserDirectory)
├── routes/       # Route registry for public routes
└── helpers/      # Bypass decider, security helpers
```

### Key Components

| Component      | File                                     | Purpose                                         |
| -------------- | ---------------------------------------- | ----------------------------------------------- |
| Token Service  | `tokens/OgiriTokenService.kt`            | Token CRUD, validation, rotation, cleanup       |
| Auth Filter    | `web/OgiriTokenAuthenticationFilter.kt`  | Request authentication, token rotation          |
| Auth Header    | `core/AuthHeader.kt`                     | HTTP header/cookie parsing and response writing |
| Sub-tokens     | `tokens/OgiriSubTokenRegistration.kt`    | Pluggable token types (device, chat, API)       |
| Route Registry | `routes/OgiriRouteRegistry.kt`           | Public/protected route declarations             |
| Config         | `config/OgiriConfigurationProperties.kt` | All configuration properties                    |

## Build Commands

```bash
# Build
./gradlew build

# Test
./gradlew test

# Test with coverage report
./gradlew test jacocoTestReport
# View: open ogiri-core/build/reports/jacoco/test/html/index.html

# Format code (required before commit)
./gradlew spotlessApply

# Check formatting
./gradlew spotlessCheck

# Security dependency check
./gradlew dependencyCheckAnalyze

# Install git hooks (recommended for development)
./gradlew setupDev
```

## Code Conventions

### Formatting

- **Kotlin**: ktfmt (0.43) via Spotless
- **Java**: Google Java Format (1.22.0)
- All files must have Apache 2.0 license header (see `spotless.license.kt`)
- Run `./gradlew spotlessApply` before committing

### Testing

- JUnit 5 with Spring Boot Test
- Minimum 50% code coverage enforced by JaCoCo
- Test files mirror source structure in `src/test/kotlin/`

## Configuration Properties

All properties prefixed with `ogiri.`:

```yaml
ogiri:
  security:
    register-filter: true # Auto-register SecurityFilterChain
  auth:
    max-clients: 10 # Max tokens per user
    batch-grace-seconds: 5 # Batch request window
    token-lifespan-days: 14 # Token TTL
    rotate-on-write-only: false # Only rotate on POST/PUT/DELETE
    rotate-stale-seconds: 0 # Force rotation threshold (0=disabled)
    register-token-service: true # Auto-register TokenService
  cleanup:
    enabled: true # Auto-cleanup job
    interval-ms: 21600000 # 6 hours
  cookies:
    enabled: true # Set auth cookies
    secure: true # Secure flag
    http-only: true # HttpOnly flag
    same-site: Strict # SameSite attribute
  cache:
    max-size: 10000 # Max cached token comparisons
    expiry-minutes: 60 # Cache entry TTL
```

## Authentication Flow

1. Request arrives at `OgiriTokenAuthenticationFilter`
2. `AuthenticationBypassDecider` checks if auth can be skipped
3. Headers extracted via `extractAuthHeader()` (headers -> cookies -> Bearer)
4. Token validated via `OgiriTokenService.validToken()`
5. Batch detection determines if rotation needed
6. Response headers set via `appendAuthHeaders()`

## Security Considerations

- Tokens are BCrypt-hashed before storage (never store plaintext)
- Token comparison results cached for 1 hour (Caffeine cache)
- Grace period allows batch requests without token thrashing
- Sub-tokens scoped to parent APP token
- See `SECURITY.md` for full security policy

## Sample Applications

- `sample/sample-java/` - Java integration example
- `sample/sample-kotlin/` - Kotlin integration example

## Key Extension Points

1. **Custom Token Implementation**: Extend `OgiriToken` interface
2. **Custom User Directory**: Implement `OgiriUserDirectory` SPI
3. **Custom Sub-tokens**: Implement `OgiriSubTokenRegistration`
4. **Custom Routes**: Implement `OgiriRouteRegistry`
5. **Custom Token Service**: Extend `OgiriTokenService<T>`
