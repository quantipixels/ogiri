# Architecture & Repo Structure

## Project Info

- **Organization**: Quantipixels (com.quantipixels.ogiri)
- **License**: Apache 2.0
- **Runtime**: Java 17+
- **Database**: Database-agnostic (pluggable framework)

## Modules

- **ogiri-core**: Core library (tokens, auth, config)
- **ogiri-jpa**: JPA persistence helpers
- **sample/sample-java**: Java integration example
- **sample/sample-kotlin**: Kotlin integration example
- **docs**: MkDocs documentation
- **.github**: CI/CD workflows

## Core Package Structure

Base: `ogiri-core/src/main/kotlin/com/quantipixels/ogiri/security/`

```
├── config/       Auto-configuration & properties
├── core/         Auth headers, identifiers, JSON codec
├── tokens/       Token service, repository, sub-tokens
├── web/          Authentication filter, entry point
├── spi/          Service Provider Interfaces (OgiriUser, OgiriUserDirectory)
├── routes/       Route registry for public routes
└── helpers/      Bypass decider, security helpers
```

## Key Components

| Component | File | Purpose |
|-----------|------|---------|
| **Token Service** | `tokens/OgiriTokenService.kt` | CRUD, validation, rotation, cleanup |
| **Auth Filter** | `web/OgiriTokenAuthenticationFilter.kt` | Request auth, rotation |
| **Auth Header** | `core/AuthHeader.kt` | HTTP header/cookie parsing, response writing |
| **Sub-tokens** | `tokens/OgiriSubTokenRegistration.kt` | Pluggable token types |
| **Route Registry** | `routes/OgiriRouteRegistry.kt` | Public/protected routes |
| **Config** | `config/OgiriConfigurationProperties.kt` | All config properties |

## Extension Points

1. **Custom Token**: Extend `OgiriToken` interface
2. **Custom User Directory**: Implement `OgiriUserDirectory` SPI
3. **Custom Sub-tokens**: Implement `OgiriSubTokenRegistration`
4. **Custom Routes**: Implement `OgiriRouteRegistry`
5. **Custom Token Service**: Extend `OgiriTokenService<T>`

## Test Location

- Tests: `ogiri-core/src/test/kotlin/<package>/<Name>Test.kt`
- SQL resources: `ogiri-core/src/main/resources/ogiri/db`

## Persistence Design

⚠️ Library is database-agnostic. Do not add module-specific persistence to `ogiri-core`.

For extensions, implement `OgiriTokenRepository` and `OgiriToken` or extend `OgiriBaseToken` per docs.
