# Development Guide

This guide is for contributors and developers working on the ogiri codebase itself. For configuration and usage of ogiri in your application, see [CONFIGURATION.md](./docs/CONFIGURATION.md).

## Development Commands

### Build and Testing

- `./gradlew build` – Compile all modules (ogiri-core + samples) and run full test suite
- `./gradlew :ogiri-core:build` – Build only the core library
- `./gradlew test` – Run unit and integration tests for all modules
- `./gradlew :ogiri-core:test` – Run tests for core library only
- `./gradlew clean` – Remove compiled artifacts and generated files
- `./gradlew spotlessApply` – Auto-format Kotlin and SQL files according to style rules
- `./gradlew spotlessCheck` – Check formatting without modifying files

### Running Sample Applications

- `./gradlew :sample:sample-java:bootRun` – Run Java sample app (requires PostgreSQL)
- `./gradlew :sample:sample-kotlin:bootRun` – Run Kotlin sample app (requires PostgreSQL)

### Publishing and Release

- `./gradlew publish` – Publish snapshot versions to OSSRH (automatic on main branch via CI/CD)
- `./gradlew release` – Release to Maven Central (use in CI/CD or locally with `OSSRH_*` env vars)
- `./gradlew bumpVersion` – Bump patch version in all build files
- `./gradlew bumpVersion -PnewVersion=X.Y.Z` – Bump to specific version

## Project Structure

The project is a multi-module Gradle build with the following organization:

```
ogiri/
├── ogiri-core/                      # Main security library (published to Maven Central)
│   ├── src/main/kotlin/             # Kotlin source code for authentication components
│   ├── src/test/kotlin/             # JUnit 5 tests covering token service, filters, etc.
│   ├── src/main/resources/ogiri/db/ # Default PostgreSQL schema (ogiri-user-tokens.sql)
│   └── build.gradle.kts             # Library-specific build config (JPA, publishing)
├── sample/
│   ├── sample-java/                 # Pure Java Spring Boot application example
│   │   ├── src/main/java/           # Java classes (TokenUserDirectory, TokenRepository, etc.)
│   │   └── src/main/resources/      # application.yml with ogiri configuration
│   ├── sample-kotlin/               # Kotlin Spring Boot application example
│   │   ├── src/main/kotlin/         # Kotlin classes (same components as Java sample)
│   │   └── src/main/resources/      # application.yml with ogiri configuration
│   └── README.md                    # Sample app setup and usage guide
├── gradle/
│   └── version.gradle.kts           # Centralized version management (git tags, env vars)
├── .github/workflows/               # GitHub Actions CI/CD pipelines
│   ├── build.yml                    # Build workflow
│   ├── test.yml                     # Test workflow
│   ├── lint.yml                     # Lint workflow
│   ├── release.yml                  # Release to Maven Central (tag-based)
│   └── snapshot.yml                 # Snapshot deployment (main branch)
├── release.gradle.kts               # Release tasks (bumpVersion, release, etc.)
├── build.gradle.kts                 # Root build file (common config for subprojects)
├── settings.gradle.kts              # Module definitions and version catalogs
└── DEVELOPMENT.md                   # Development guidelines (this file)
```

**Key Points:**
- **ogiri-core** is the only module published to Maven Central; samples are for reference
- **Samples** demonstrate both Java and Kotlin integrations without requiring Kotlin for Java-only projects
- **CI/CD** runs on every commit; releases to Maven Central are triggered manually or via git tags
- **Version management** supports git tags, environment variables, and local manual bumping

## Architecture Overview

The library is organized into layers around token-based authentication:

### Core Package (`security/core/`)

- **AuthHeader**: Serializes/deserializes authentication headers (access-token, client, uid, expiry, Authorization bearer)
- **IdentifierPolicy**: Validates user/client identifiers (extendable for custom validation rules)
- **JsonCodec**: Handles Base64 encoding/decoding of token payloads
- **SecurityServiceException**: Standard exception for auth failures (user-facing error codes)

### Tokens Package (`security/tokens/`)

- **BaseToken / Token**: JPA entity representing a stored token with hash, expiry, and rotation history
- **TokenService**: Core business logic for token issuance, validation, rotation, and sub-token management
- **TokenRepository**: Data access interface (TokenUserDirectory implementations must provide)
- **SubTokenRegistration**: SPI for plugins to register custom sub-tokens (e.g., chat, device) with custom expiry/client logic
- **TokenCleanupJob**: Scheduled job to delete expired tokens

### Web/Filter Package (`security/web/`)

- **OgiriTokenAuthenticationFilter**: OncePerRequestFilter that validates tokens, decides rotation, and populates SecurityContext
- **OgiriAuthenticationEntryPoint**: Responds to auth failures with configurable error messages

### SPI Package (`security/spi/`)

- **TokenUserDirectory**: Host app must implement to load users by id/email/username and record logins
- **TokenUser**: Data class representing an authenticated user
- **RouteRegistry**: Host app implements to declare public/unauthenticated routes

### Helpers Package (`security/helpers/`)

- **AuthenticationBypassDecider**: Decides if a request can skip auth (whitelisted paths, CORS preflight, already authenticated)
- **SecurityHelpers**: Utilities for path matching and safe identifier validation

### Routes Package (`security/routes/`)

- **RouteCatalog**: Aggregates all RouteRegistry implementations into a queryable route catalog
- **Route**: Represents an HTTP endpoint with optional role/scope requirements

### Config Package (`security/config/`)

- **OgiriSecurityAutoConfiguration**: Spring Boot auto-configuration that wires all beans, registers the filter in the security chain, and optionally runs TokenCleanupJob

## Request Authentication Lifecycle

1. **Filter Entry**: `OgiriTokenAuthenticationFilter.doFilterInternal()` is called once per request
2. **Bypass Check**: `AuthenticationBypassDecider.canSkip()` returns true for:
   - Already authenticated users
   - Public routes (from RouteRegistry)
   - CORS preflight (OPTIONS requests)
   - Health/docs paths
3. **Header Extraction**: `AuthHeader.extractAuthHeader()` parses headers (access-token, client, uid, expiry)
4. **Token Validation**: `TokenService.validToken()` verifies the hashed token against database, allowing grace periods for token rotation
5. **Rotation Decision**: Based on batch window (`ogiri.auth.batch-grace-seconds`) and staleness threshold (`ogiri.auth.rotate-stale-seconds`):
   - Within batch window: only update `lastUsedAt`, no new headers
   - Outside batch window: call `createNewAuthToken()` to rotate and emit refreshed headers
6. **Success**: Populate SecurityContext with `UsernamePasswordAuthenticationToken` and append auth headers
7. **Failure**: Clear SecurityContext and delegate to `AuthenticationEntryPoint`

## Code Style and Conventions

- **Language**: Kotlin with Spring idioms
- **Indentation**: 2 spaces
- **Nullability**: Explicit with `?` and `.let` blocks; avoid `!!` outside tests
- **Naming**: PascalCase for classes/interfaces, camelCase for functions/properties
- **Formatting**: Enforced by Spotless; run `spotlessApply` before committing
- **Comments**: Use sparingly; prefer self-evident code. Add comments only where logic isn't clear.
- **Test Names**: Descriptive backticked names inside `@Test`, e.g., `` `should rotate token outside batch window` ``
- **License**: Don't write license info in files. Spotless handles that automatically.

## Package Organization

Keep classes small and cohesive:

- `com.quantipixels.ogiri.security.core` – Token models, headers, exceptions
- `com.quantipixels.ogiri.security.tokens` – Service and data access
- `com.quantipixels.ogiri.security.web` – Filters and entry points
- `com.quantipixels.ogiri.security.spi` – Interfaces for host app implementation
- `com.quantipixels.ogiri.security.helpers` – Utilities for validation and routing
- `com.quantipixels.ogiri.security.routes` – Route discovery and catalog
- `com.quantipixels.ogiri.security.config` – Spring configuration and auto-config

## Security Considerations

1. **Logging**: Never log raw token values. Use helpers in `SecurityHelpers` when parsing/validating identifiers.
2. **TokenUserDirectory**: Fetch users securely and record successful logins; wrap errors with `SecurityServiceException` to avoid leaking sensitive details.
3. **RouteRegistry**: Always register unauthenticated endpoints to prevent accidental lockouts.
4. **Header Stability**: Token header keys and database column names are contracts with host apps—changes require coordination and migration notes.
5. **Identifier Validation**: Use `IdentifierPolicy` for all user/client ID validation before database queries.

## Testing

### Current Test Coverage

**Overall Coverage:** ~25-30% (estimated)
**Test Files:** 4 files
**Test Methods:** 13 methods
**Total Test Code:** ~929 lines

Run tests with coverage:
```bash
./gradlew test
# Report: ogiri-core/build/reports/jacoco/test/html/index.html
```

### Coverage by Component

| Component | Coverage | Status |
|-----------|----------|--------|
| **AuthenticationBypassDecider** | 100% | ✅ Complete |
| **AuthHeader** | 90% | ✅ Excellent |
| **OgiriTokenAuthenticationFilter** | 70% | ✅ Good |
| **TokenService (Sub-tokens)** | 25% | ⚠️ Partial |
| **OgiriSecurityAutoConfiguration** | 0% | ❌ Untested |
| **OgiriAuthenticationEntryPoint** | 0% | ❌ Untested |

### Well-Tested Components

**AuthenticationBypassDeciderTest.kt (100% coverage)**
- Bypass for already authenticated users
- Bypass for public routes (whitelisted paths)
- Bypass for CORS preflight requests (OPTIONS method)
- Bypass for health/docs endpoints
- Path matching with wildcards and patterns

**AuthHeaderTest.kt (90% coverage)**
- Header serialization/deserialization
- Single token and multiple sub-token formats
- Timestamp parsing (ISO-8601)
- Base64 encoding/decoding
- Edge cases (null values, empty sub-tokens)

**OgiriTokenAuthenticationFilterTest.kt (70% coverage)**
- Request authentication flow
- Token validation and error handling
- Batch window grace period (no rotation)
- Token rotation outside batch window
- Write-only rotation mode (POST/PUT/DELETE)
- SecurityContext population

### Under-Tested Components (Expansion Opportunities)

**TokenService.kt** - Primary token creation and rotation logic untested
- `createNewAuthToken()` – Token creation with sub-tokens
- `validToken()` – Token validation with grace period reuse
- `cleanOldTokens()` – Max clients enforcement
- `issueSubTokens()` – Sub-token lifecycle

**OgiriSecurityAutoConfiguration.kt** – No tests
- Bean creation with custom properties
- Conditional configuration
- TokenCleanupJob registration
- SecurityFilterChain wiring

**OgiriAuthenticationEntryPoint.kt** – No tests
- Error response formatting
- HTTP status codes
- Error message handling

### Testing Best Practices

- Use in-memory fakes for repositories (e.g., `InMemoryTokenRepository`)
- Mock HTTP requests/responses when asserting header behavior
- When touching token rotation or expiry logic, add tests for `AuthHeader` serialization
- When modifying the SQL schema, add tests reflecting new persistence expectations
- Test both success and failure paths
- Use given-when-then test structure for clarity

### Priority Areas for Expansion

**High Priority (Reach 50% coverage)**
1. **TokenService.createNewAuthToken()** – Primary token creation
   - Lines: ~50, Complexity: High
   - Tests needed: 8-10 methods
   - Focus: Token issuance, hashing, database persistence

2. **OgiriAuthenticationEntryPoint** – Error handling
   - Lines: ~40, Complexity: Medium
   - Tests needed: 6-8 methods
   - Focus: HTTP responses, error messages

3. **Route.kt** – Path matching
   - Lines: ~30, Complexity: Low
   - Tests needed: 5-6 methods
   - Focus: Pattern matching, path validation

**Medium Priority (Reach 65% coverage)**
1. **OgiriSecurityAutoConfiguration** – Bean wiring
   - Lines: ~120, Complexity: High
   - Tests needed: 10-12 methods

2. **TokenCleanupJob** – Scheduled cleanup
   - Lines: ~30, Complexity: Medium
   - Tests needed: 4-5 methods

3. **Full TokenService Integration** – All token operations
   - Lines: ~300, Complexity: High
   - Tests needed: 15-20 methods

## Common Development Tasks

### Adding a New Sub-Token Type

1. Implement `SubTokenRegistration` bean
2. Define `name`, `clientIdFor()`, `expiry()`, and optionally `includeByDefault`
3. Test issuance in `TokenServiceSubTokenTest`
4. Update documentation with example configuration

### Modifying Token Rotation Logic

1. Update `TokenService.rotateTokensIfNeeded()` or batch grace logic
2. Add test cases in `OgiriTokenAuthenticationFilterTest` covering:
   - Requests within/outside batch window
   - Staleness threshold enforcement
   - Write-only rotation mode
3. Document property changes in configuration documentation

### Extending Token Entity

1. Create custom class extending `BaseToken`
2. Implement `TokenRepository<MyToken>` for data access
3. Provide `TokenService<MyToken>` bean (optionally subclass and override `createTokenEntity`)
4. Disable auto-configuration with `ogiri.security.register-filter=false` and wire custom SecurityFilterChain

### Customizing Identifier Validation

1. Implement custom `IdentifierPolicy` bean
2. Override default in auto-configuration (bean is @ConditionalOnMissingBean)
3. Add tests for edge cases (special characters, length limits, etc.)

## Dependency Versions

Pinned in `settings.gradle.kts`:
- **Kotlin**: 2.0.21
- **Spring Boot**: 3.5.7
- **Java Target**: 17 (both compile and runtime)

## Version Management

**Current Version:** 1.0.1

ogiri uses centralized version management in `settings.gradle.kts` and follows [Semantic Versioning](https://semver.org/):

**MAJOR.MINOR.PATCH**
- **MAJOR** – Incompatible API changes (breaking changes)
- **MINOR** – Backward-compatible new features
- **PATCH** – Backward-compatible bug fixes

Example: `1.0.0` (first release) → `1.0.1` (patch) → `1.1.0` (minor) → `2.0.0` (major)

### Version Resolution (Precedence)

When building, version is resolved in this order:

1. **Environment Variable** (highest priority)
   ```bash
   RELEASE_VERSION=1.0.2 ./gradlew build
   ```

2. **Gradle Property**
   ```bash
   ./gradlew -PRELEASE_VERSION=1.0.2 build
   ```

3. **Git Tag** (detected by GitHub Actions)
   ```bash
   git tag v1.0.2
   ```

4. **Default** (lowest priority)
   - Falls back to version in `settings.gradle.kts`

### Bumping Version for Release

To release a new version (e.g., 1.0.1 → 1.0.2):

```bash
# 1. Update settings.gradle.kts
val projectVersion = ... ?: "1.0.2"

# 2. Update CHANGELOG.md
## [1.0.2] - 2025-12-06
### Fixed
- Bug fixes in this release

# 3. Commit and tag
git add settings.gradle.kts CHANGELOG.md
git commit -m "chore: bump version to 1.0.2"
git tag v1.0.2
git push origin main v1.0.2
```

### Pre-release Versions

For development and testing, use:
- `-SNAPSHOT` – Development snapshot (auto-deployed to Sonatype snapshots)
- `-RC1`, `-BETA`, `-ALPHA` – Release candidates and beta versions

```bash
RELEASE_VERSION=1.0.2-RC1 ./gradlew build
```

## CI/CD Pipeline and Releases

The project uses GitHub Actions for automated testing and publishing to Maven Central.

### Workflows

1. **build.yml** – Compiles all modules without tests
2. **test.yml** – Runs full test suite and coverage reports
3. **lint.yml** – Checks code formatting via spotlessCheck
4. **release.yml** – Triggered by git tags; publishes to Maven Central
5. **snapshot.yml** – Automatic snapshot deployment on every push to main

### Quick Start - Release

The quickest way to release:

```bash
# 1. Update version in settings.gradle.kts and CHANGELOG.md
# 2. Create and push git tag
git tag v1.0.2
git push origin v1.0.2

# 3. GitHub Actions automatically:
#    - Detects the tag
#    - Builds and signs artifacts
#    - Publishes to Maven Central
#    - Creates GitHub release
```

### Release Workflows

#### 1. Tag-Based Release (Production)

**Trigger:** Push git tag matching `v*.*.*`

```bash
git tag v1.0.2
git push origin v1.0.2
```

**Workflow file:** `.github/workflows/release.yml`

**Actions:**
1. Extracts version from git tag
2. Builds all modules with extracted version
3. Signs artifacts with GPG key
4. Publishes to Maven Central (release repository)
5. Creates GitHub release with artifacts

**Result:** Published to https://repo1.maven.org/maven2/com/quantipixels/ogiri/

#### 2. Snapshot Deployment (Main Branch)

**Trigger:** Push to `main` branch

**Workflow file:** `.github/workflows/snapshot.yml`

**Actions:**
1. Builds all modules with `-SNAPSHOT` suffix
2. Publishes to Sonatype snapshots repository
3. Available for testing before full release

**Result:** Published to https://oss.sonatype.org/content/repositories/snapshots/

#### 3. Build & Test (All PRs/Pushes)

**Trigger:** All pushes and pull requests

**Workflow files:** `.github/workflows/build.yml`, `.github/workflows/test.yml`, `.github/workflows/lint.yml`

**Actions:**
1. Compiles all modules
2. Runs full test suite with coverage
3. Validates code formatting
4. Does NOT publish artifacts

### GitHub Actions Configuration

**Required Secrets** (Settings → Secrets and variables → Actions):

| Secret | Purpose |
|--------|---------|
| `OSSRH_USERNAME` | Sonatype OSSRH account username |
| `OSSRH_PASSWORD` | Sonatype OSSRH account password/token |
| `GPG_KEY_ID` | GPG key ID (short form) |
| `GPG_PASSPHRASE` | GPG key passphrase |
| `GPG_PRIVATE_KEY` | GPG private key (base64 encoded) |

**To export GPG key for GitHub:**

```bash
gpg --export-secret-key <KEY_ID> | base64
# Copy output to GPG_PRIVATE_KEY secret
```

### Manual Release (Local)

If you need to publish locally (not recommended):

```bash
# Prerequisites:
# - Sonatype credentials in ~/.gradle/gradle.properties
# - GPG key configured locally

# 1. Verify tests pass
./gradlew test

# 2. Build and publish
./gradlew publish -Psigning.gnupg.executable=gpg
```

### Release Checklist

Before releasing, verify:

- [ ] All tests pass: `./gradlew test`
- [ ] Code formatting verified: `./gradlew spotlessCheck`
- [ ] Documentation updated
- [ ] CHANGELOG.md updated with version
- [ ] No uncommitted changes
- [ ] Version updated in `settings.gradle.kts`
- [ ] Git tag created: `git tag vX.Y.Z`
- [ ] Tag pushed: `git push origin vX.Y.Z`
- [ ] GitHub Actions workflow completed successfully
- [ ] Artifacts appear on Maven Central (10-30 minutes)


## Commit and PR Guidelines

- Use Conventional Commits: `feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`
- Describe behavior changes and testing performed (`./gradlew test`)
- Call out database or auto-configuration changes with migration/config notes
- Include header/request examples when modifying filter behavior or token formats
- Ensure `spotlessCheck` passes before committing
- For release commits, use `chore: bump version to X.Y.Z` format

## References

- **[CONFIGURATION.md](./docs/CONFIGURATION.md)** – Configuration properties and options
- **[AUTHENTICATION.md](./docs/AUTHENTICATION.md)** – Detailed request lifecycle, rotation policies, headers, error handling
- **[DATABASE.md](./docs/DATABASE.md)** – Database integration patterns
- **[SUB_TOKENS.md](./docs/SUB_TOKENS.md)** – Sub-token creation and usage
- **[README.md](./README.md)** – Project overview and quick start
- **[CONTRIBUTING.md](./CONTRIBUTING.md)** – Contribution guidelines and code standards
