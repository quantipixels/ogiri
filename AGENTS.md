# AGENTS.md

AI assistant guidance for the ogiri security library.

## Project Overview

**Ògiri** is a Spring Boot security library providing reusable token-based authentication with pluggable sub-token support. It's database-agnostic and designed for applications requiring JWT-like token management without external dependencies.

**Key characteristics:**
- Database-agnostic (works with JPA, MongoDB, Redis, or custom persistence)
- Auto-configured via Spring Boot
- Supports sub-tokens for specialized use cases (chat, device, API tokens)
- BCrypt hashing with configurable rotation policies

## Quick Reference

### Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile all modules and run tests |
| `./gradlew test` | Run test suite only |
| `./gradlew :ogiri-core:test` | Run core library tests only |
| `./gradlew spotlessApply` | Auto-format code |
| `./gradlew spotlessCheck` | Verify formatting |
| `./gradlew :sample:sample-kotlin:bootRun` | Run Kotlin sample (requires PostgreSQL) |
| `./gradlew :sample:sample-java:bootRun` | Run Java sample (requires PostgreSQL) |

### Version & Release

| Command | Description |
|---------|-------------|
| `./gradlew bumpVersion -PnewVersion=X.Y.Z` | Set specific version |
| `./gradlew publish` | Publish to Maven Central |
| `git tag v1.0.2 && git push origin v1.0.2` | Trigger release workflow |

Current version is defined in `settings.gradle.kts`. Override with `RELEASE_VERSION` environment variable.

## Project Structure

```
ogiri/
├── ogiri-core/                      # Main library (published to Maven Central)
│   ├── src/main/kotlin/
│   │   └── com/quantipixels/ogiri/security/
│   │       ├── core/                # AuthHeader, JsonCodec, exceptions, IdentifierPolicy
│   │       ├── tokens/              # OgiriToken, OgiriBaseToken, OgiriTokenService, OgiriTokenRepository
│   │       ├── web/                 # OgiriTokenAuthenticationFilter
│   │       ├── spi/                 # OgiriUserDirectory, OgiriUser, RouteRegistry
│   │       ├── helpers/             # AuthenticationBypassDecider, SecurityHelpers
│   │       ├── routes/              # RouteCatalog, Route
│   │       └── config/              # OgiriSecurityAutoConfiguration
│   ├── src/test/kotlin/             # JUnit 5 tests
│   └── src/main/resources/ogiri/db/ # Bundled SQL schemas
├── sample/
│   ├── sample-java/                 # Pure Java integration example
│   └── sample-kotlin/               # Kotlin integration example
├── docs/                            # Documentation (MkDocs site)
└── .github/workflows/               # CI/CD pipelines
```

## Key Components

### Host App Must Implement

| Interface | Purpose | Location |
|-----------|---------|----------|
| `OgiriUserDirectory` | Load users by id/email/username, record logins | `security/spi/` |
| `RouteRegistry` | Declare unauthenticated routes | `security/spi/` |
| `OgiriToken` | Token entity contract (interface-first design) | `security/tokens/` |
| `OgiriTokenRepository<T>` | Token persistence (JPA, MongoDB, etc.) | `security/tokens/` |

### Library Provides

| Component | Purpose | Location |
|-----------|---------|----------|
| `OgiriBaseToken` | Convenience base class implementing OgiriToken | `security/tokens/` |
| `OgiriTokenService<T>` | Token creation, validation, rotation | `security/tokens/` |
| `OgiriTokenAuthenticationFilter` | Request authentication filter | `security/web/` |
| `AuthHeader` | Header serialization/deserialization | `security/core/` |
| `SubTokenRegistration` | Custom sub-token definitions | `security/tokens/` |
| `OgiriSecurityAutoConfiguration` | Spring Boot auto-config | `security/config/` |

## Configuration Properties

All properties prefixed with `ogiri`:

| Property | Default | Description |
|----------|---------|-------------|
| `ogiri.security.register-filter` | `true` | Auto-register SecurityFilterChain |
| `ogiri.auth.max-clients` | `24` | Max active tokens per user |
| `ogiri.auth.batch-grace-seconds` | `5` | Grace period before rotation |
| `ogiri.auth.token-lifespan-days` | `14` | Token lifetime |
| `ogiri.auth.rotate-on-write-only` | `false` | Only rotate on POST/PUT/DELETE |
| `ogiri.auth.rotate-stale-seconds` | `0` | Force rotation after N seconds |
| `ogiri.cleanup.enabled` | `true` | Enable cleanup job |
| `ogiri.cleanup.cron` | `0 0 * * * *` | Cleanup schedule |

## Request Authentication Lifecycle

1. `OgiriTokenAuthenticationFilter.doFilterInternal()` intercepts request
2. `AuthenticationBypassDecider.canSkip()` checks if auth is needed
3. `AuthHeader.extractAuthHeader()` parses headers (or Bearer token)
4. `TokenService.validToken()` validates against database
5. Token rotation based on batch window and staleness policy
6. `SecurityContext` populated with authenticated user

## Coding Conventions

- **Language:** Kotlin with Spring idioms
- **Indentation:** 2 spaces
- **Nullability:** Explicit with `?`; avoid `!!` outside tests
- **Naming:** PascalCase for classes, camelCase for functions
- **Tests:** Descriptive backticked names: `` `should rotate token outside batch window` ``
- **Formatting:** Enforced by Spotless
- **Import:** Explicit import. No catch-all/generic (*) imports

## Testing Guidelines

- Use JUnit 5 with Spring test utilities
- Place tests in `src/test/kotlin/<package>/<Name>Test.kt`
- Use in-memory fakes (e.g., `InMemoryTokenRepository`) over real databases
- When modifying token logic, add tests for `AuthHeader` serialization
- When changing schemas, update tests to reflect new persistence expectations

### Current Test Coverage

| Component | Coverage |
|-----------|----------|
| AuthenticationBypassDecider | 100% |
| AuthHeader | 90% |
| OgiriTokenAuthenticationFilter | 70% |
| OgiriTokenService (sub-tokens) | 25% |
| OgiriSecurityAutoConfiguration | 0% |

## Security Considerations

1. **Never log raw tokens** - Use `SecurityHelpers` for parsing
2. **Wrap auth errors** - Use `SecurityServiceException` to avoid leaking details
3. **Register public routes** - Prevent accidental lockouts via `RouteRegistry`
4. **Header stability** - Header keys are API contracts; changes need migration notes
5. **Identifier validation** - Use `IdentifierPolicy` before database queries

## Common Development Tasks

### Adding a Sub-Token Type

1. Implement `SubTokenRegistration` bean
2. Define `name`, `clientIdFor()`, `expiry()`, `includeByDefault`
3. Add tests in `TokenServiceSubTokenTest`
4. Document in `docs/SUB_TOKENS.md`

### Modifying Token Rotation

1. Update `TokenService.rotateTokensIfNeeded()` or batch logic
2. Add tests in `OgiriTokenAuthenticationFilterTest`
3. Update `docs/CONFIGURATION.md` with property changes

### Extending Token Entity (Interface-First Design)

**Option 1: Direct Interface Implementation (Maximum Flexibility)**
1. Create class implementing `OgiriToken` interface
2. Add custom fields as needed
3. Implement `OgiriTokenRepository<MyToken>`
4. Provide custom `OgiriTokenService<MyToken>` bean

**Option 2: Extend OgiriBaseToken (Convenience)**
1. Create class extending `OgiriBaseToken`
2. Implement remaining abstract properties
3. Implement `OgiriTokenRepository<MyToken>`
4. Provide custom `OgiriTokenService<MyToken>` bean

See [Interface-First Design](docs/interface-first-design.md) for detailed examples.

## Documentation

| Topic | File |
|-------|------|
| Quickstart (5 min) | `docs/quickstart.md` |
| Interface-First Design | `docs/interface-first-design.md` |
| Implementation Guide | `docs/implementation-guide.md` |
| Configuration | `docs/configuration.md` |
| Database patterns | `docs/database.md` |
| Sub-tokens | `docs/sub-tokens.md` |
| Auth flow | `docs/authentication.md` |
| Development | `docs/development.md` |
| Contributing | `docs/contributing.md` |

## Commit Guidelines

Use Conventional Commits:
- `feat: add chat sub-token renewal`
- `fix: adjust expiry parsing`
- `refactor: extract validation logic`
- `docs: update configuration guide`
- `test: add rotation edge cases`
- `chore: bump version to 1.0.2`

## CI/CD Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | All pushes | Compile modules |
| `test.yml` | All pushes | Run tests with coverage |
| `lint.yml` | All pushes | Verify formatting |
| `release.yml` | Tag `v*.*.*` | Publish to Maven Central |
| `snapshot.yml` | Push to `main` | Deploy snapshots |

## Dependencies

- **Java:** 17+
- **Kotlin:** 2.0.21
- **Spring Boot:** 3.5.7
