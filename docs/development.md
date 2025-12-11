# Development Guide

Guide for contributors working on the ogiri codebase.

## Prerequisites

- Java 17+
- Kotlin 2.0.x
- Gradle 8.x
- PostgreSQL (for running samples)

## Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile all modules and run tests |
| `./gradlew test` | Run test suite only |
| `./gradlew :ogiri-core:test` | Run core library tests only |
| `./gradlew clean` | Remove build artifacts |
| `./gradlew spotlessApply` | Auto-format code |
| `./gradlew spotlessCheck` | Verify formatting |

### Running Samples

```bash
./gradlew :sample:sample-kotlin:bootRun  # Kotlin sample
./gradlew :sample:sample-java:bootRun    # Java sample
```

Requires PostgreSQL on `localhost:5432`. See [sample/README.md](../sample/README.md) for setup.

## Project Structure

```
ogiri/
├── ogiri-core/                      # Main library (published to Maven Central)
│   ├── src/main/kotlin/             # Source code
│   │   └── com/quantipixels/ogiri/security/
│   │       ├── core/                # AuthHeader, JsonCodec, exceptions
│   │       ├── tokens/              # TokenService, TokenRepository, BaseToken
│   │       ├── web/                 # OgiriTokenAuthenticationFilter
│   │       ├── spi/                 # OgiriUserDirectory, OgiriUser, RouteRegistry
│   │       ├── helpers/             # AuthenticationBypassDecider, SecurityHelpers
│   │       ├── routes/              # RouteCatalog, Route
│   │       └── config/              # OgiriSecurityAutoConfiguration
│   ├── src/test/kotlin/             # JUnit 5 tests
│   └── src/main/resources/ogiri/db/ # Bundled SQL schemas
├── sample/
│   ├── sample-java/                 # Pure Java example
│   └── sample-kotlin/               # Kotlin example
├── docs/                            # Documentation (MkDocs)
└── .github/workflows/               # CI/CD pipelines
```

## Code Style

- **Indentation:** 2 spaces
- **Nullability:** Explicit with `?`; avoid `!!` outside tests
- **Naming:** PascalCase for classes, camelCase for functions
- **Tests:** Backticked names: `` `should rotate token outside batch window` ``
- **Formatting:** Run `spotlessApply` before committing

## Testing

### Running Tests

```bash
./gradlew test                    # All tests
./gradlew :ogiri-core:test        # Core only
```

Coverage report: `ogiri-core/build/reports/jacoco/test/html/index.html`

### Test Guidelines

- Use JUnit 5 with Spring test utilities
- Place tests in `src/test/kotlin/<package>/<Name>Test.kt`
- Use in-memory fakes (e.g., `InMemoryTokenRepository`)
- When modifying token logic, add `AuthHeader` serialization tests
- When changing schemas, update persistence tests

### Current Coverage

| Component | Coverage |
|-----------|----------|
| AuthenticationBypassDecider | 100% |
| AuthHeader | 90% |
| OgiriTokenAuthenticationFilter | 70% |
| TokenService (sub-tokens) | 25% |
| OgiriSecurityAutoConfiguration | 0% |

## Git Hooks

Install hooks for code quality enforcement:

```bash
./gradlew setupDev
```

- **Pre-commit:** Runs `spotlessCheck`
- **Pre-push:** Runs full build

## Commit Guidelines

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add chat sub-token renewal
fix: adjust expiry parsing
refactor: extract validation logic
docs: update configuration guide
test: add rotation edge cases
chore: bump version to 1.0.2
```

## Pull Request Process

1. Create feature branch from `main`
2. Make changes with tests
3. Run `./gradlew build spotlessCheck`
4. Push and create PR
5. Link related issues
6. Wait for CI and review

## Version Management

Version is defined in `settings.gradle.kts`:

```kotlin
val projectVersion = System.getenv("RELEASE_VERSION") ?: "1.0.1"
```

### Override Version

```bash
RELEASE_VERSION=1.0.2 ./gradlew build
```

### Bump Version

```bash
./gradlew bumpVersion -PnewVersion=1.0.2
```

## Release Process

### Automated Release (Recommended)

Push a git tag to trigger the release workflow:

```bash
# 1. Update version in settings.gradle.kts
# 2. Update changelog.md
# 3. Commit changes
git add settings.gradle.kts docs/changelog.md
git commit -m "chore: bump version to 1.0.2"

# 4. Create and push tag
git tag v1.0.2
git push origin main v1.0.2
```

GitHub Actions will:
- Build and test
- Sign artifacts with GPG
- Publish to Maven Central
- Create GitHub release

### CI/CD Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `build.yml` | All pushes | Compile modules |
| `test.yml` | All pushes | Run tests with coverage |
| `lint.yml` | All pushes | Verify formatting |
| `release.yml` | Tag `v*.*.*` | Publish to Maven Central |
| `snapshot.yml` | Push to `main` | Deploy snapshots |

### Required Secrets

Configure in GitHub repository settings:

| Secret | Purpose |
|--------|---------|
| `OSSRH_USERNAME` | Sonatype username |
| `OSSRH_PASSWORD` | Sonatype password |
| `GPG_KEY_ID` | GPG key ID |
| `GPG_PASSPHRASE` | GPG passphrase |
| `GPG_PRIVATE_KEY` | Base64-encoded GPG key |

Export GPG key:
```bash
gpg --export-secret-key <KEY_ID> | base64
```

### Manual Release (Not Recommended)

```bash
export OSSRH_USERNAME=your_username
export OSSRH_PASSWORD=your_password
./gradlew test
./gradlew publish -Psigning.gnupg.executable=gpg
```

### Release Checklist

- [ ] Tests pass: `./gradlew test`
- [ ] Formatting verified: `./gradlew spotlessCheck`
- [ ] changelog.md updated
- [ ] Version updated in `settings.gradle.kts`
- [ ] Tag created and pushed
- [ ] CI workflow completed

## Common Tasks

### Adding a Sub-Token Type

1. Implement `SubTokenRegistration` bean
2. Define `name`, `clientIdFor()`, `expiry()`, `includeByDefault`
3. Add tests in `TokenServiceSubTokenTest`
4. Document in `docs/sub-tokens.md`

### Modifying Token Rotation

1. Update `TokenService.rotateTokensIfNeeded()`
2. Add tests in `OgiriTokenAuthenticationFilterTest`
3. Update `docs/configuration.md`

### Extending Token Entity

1. Create class extending `BaseToken`
2. Implement `TokenRepository<MyToken>`
3. Provide custom `TokenService<MyToken>` bean
4. Set `ogiri.security.register-filter=false`

## Security

- Never log raw tokens
- Use `SecurityServiceException` for auth errors
- Use `IdentifierPolicy` for validation
- Register public routes to prevent lockouts
