# Ògiri Agent Guide

Guidance for agentic coding assistants working in this repository.
Scope: applies repo-wide (no nested AGENTS currently). Follow everything here when editing any file.

## Project Overview

- Kotlin Spring Boot 3.5+ security library providing token-based authentication
- Organization: Quantipixels (com.quantipixels.ogiri), License: Apache 2.0
- Database-agnostic, pluggable framework for reusable auth components
- Runs on Java 17+

## Repo Map

- Root project: Spring Boot security library with Kotlin primary.
- Modules: `ogiri-core` (library), `ogiri-jpa` (JPA helpers), `sample/sample-java`, `sample/sample-kotlin`, `docs` (MkDocs), `.github` (CI).
- Tests live under `ogiri-core/src/test/kotlin` using JUnit 5.
- SQL resources: `ogiri-core/src/main/resources/ogiri/db`.

Architecture (`ogiri-core/src/main/kotlin/com/quantipixels/ogiri/security/`):

- `config/` - Spring Boot auto-configuration & properties
- `core/` - Auth headers, identifiers, JSON codec
- `tokens/` - Token service, repository, sub-tokens
- `web/` - Authentication filter, entry point
- `spi/` - Service Provider Interfaces (OgiriUser, OgiriUserDirectory)
- `routes/` - Route registry for public routes
- `helpers/` - Bypass decider, security helpers

Key components:

- Token Service: `tokens/OgiriTokenService.kt` - CRUD, validation, rotation, cleanup
- Auth Filter: `web/OgiriTokenAuthenticationFilter.kt` - Request auth, rotation
- Auth Header: `core/AuthHeader.kt` - HTTP header/cookie parsing, response writing
- Sub-tokens: `tokens/OgiriSubTokenRegistration.kt` - Pluggable token types
- Route Registry: `routes/OgiriRouteRegistry.kt` - Public/protected routes
- Config: `config/OgiriConfigurationProperties.kt` - All config properties

## Tooling & Prereqs

- Java 17+, Kotlin 2.1.0 (toolchain configured), Gradle 8.x wrapper present.
- Use provided `./gradlew` (no system Gradle). Do not bump tool versions.
- PostgreSQL only required for sample apps (not for library tests).
- Spotless + ktfmt/google-java-format enforce formatting.

## Build / Lint / Test Commands

- Full build + tests: `./gradlew build`
- Tests only (all modules): `./gradlew test`
- Core tests only: `./gradlew :ogiri-core:test`
- Lint/format check: `./gradlew spotlessCheck`
- Auto-format: `./gradlew spotlessApply`
- Clean: `./gradlew clean`
- Install git hooks: `./gradlew setupDev`

## Running a Single Test

- By class (preferred): `./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.core.AuthHeaderTest"`
- By pattern: `./gradlew :ogiri-core:test --tests "*AuthHeaderTest"`
- Individual method with backticked name: `./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.tokens.TokenServiceSubTokenTest.`default sub token is issued and returned in headers`"`
- For nested tests, include `$` between class and nested class: `./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.core.AuthHeaderTest$ParseBearerTokenTests.`parseBearerToken parses valid token`"`
- Re-run failed tests only: `./gradlew :ogiri-core:test --tests $(cat ogiri-core/build/test-results/test/TESTS-failed.txt)` if file exists.

## Sample Apps (optional, need PostgreSQL)

- Kotlin sample: `./gradlew :sample:sample-kotlin:bootRun`
- Java sample: `./gradlew :sample:sample-java:bootRun`
- Configure DB at `localhost:5432`; see `sample/README.md` for creds/setup.

## Configuration Properties

All properties prefixed `ogiri.*`:

- `ogiri.security.register-filter` (true) - Auto-register SecurityFilterChain
- `ogiri.auth.max-clients` (10) - Max tokens per user
- `ogiri.auth.batch-grace-seconds` (5) - Batch request window
- `ogiri.auth.token-lifespan-days` (14) - Token TTL
- `ogiri.auth.rotate-on-write-only` (false) - Only rotate on POST/PUT/DELETE
- `ogiri.auth.rotate-stale-seconds` (3600) - Force rotation threshold (0=disabled)
- `ogiri.auth.max-bearer-token-size` (8192) - DoS protection
- `ogiri.cleanup.enabled` (true), `interval-ms` (6hrs), `batch-size` (1000)
- `ogiri.cookies.enabled` (true), `secure` (true), `http-only` (true), `same-site` (Strict), `path` (/)
- `ogiri.cache.max-size` (10000), `expiry-minutes` (60)

Startup warnings logged for: `rotate-stale-seconds: 0`, `secure: false`, `http-only: false`.

## Authentication Flow

1. Request → `OgiriTokenAuthenticationFilter`
2. `AuthenticationBypassDecider.canSkip()` checks public routes
3. Extract headers via `extractAuthHeader()` (headers → cookies → Bearer)
4. Validate token via `OgiriTokenService.validToken()`
5. Batch detection determines rotation need
6. Response headers set via `appendAuthHeaders()`

On 401: If `ogiri.cookies.enabled=true`, auth cookies cleared (maxAge=0) to prevent stale-cookie loops.

## Formatting Rules (Spotless / .editorconfig)

- Kotlin + KTS formatted with ktfmt 0.43 and license header `spotless.license.kt`.
- Java formatted with google-java-format 1.22.0.
- Markdown/YAML/TOML/ignore files run through Prettier trim + trailing newline.
- SQL formatted with dbeaver profile.
- `.editorconfig`: default indent 4, width 120; Kotlin/KTS/YAML/MD indent 2, LF endings, trim trailing whitespace, final newline required.
- Do not modify formatter versions or license headers.

## Imports

- No wildcard (`*`) imports; keep imports explicit.
- Order: Kotlin/Java stdlib first, third-party, then project packages; static imports after regular ones.
- Remove unused imports; let formatter collapse blank lines.
- Avoid fully qualified names in code when an import is possible.

## Kotlin Style

- Indentation 2 spaces; avoid tabs.
- Null-safety: prefer nullable types over `!!`; only use `!!` in tests.
- Use data classes where appropriate; favor immutability (`val` over `var`).
- Function and property names in `camelCase`; classes/interfaces/objects in `PascalCase`.
- Use expression bodies for simple returns; favor `when` over cascaded `if` where clear.
- Prefer `sealed`/`enum` for constrained types; avoid magic strings.
- Keep functions short; extract helpers in same file when scoped.

## Java Style

- Follow Google Java Format; 2-space indent via formatter output (do not hand-tune).
- Avoid Lombok; use standard constructors/builders if needed.
- Prefer `final` fields where possible; avoid mutable statics.
- Use Optional sparingly; favor Kotlin nullability patterns in mixed code.

## Error Handling

- Wrap authentication/validation errors with `SecurityServiceException` to avoid leaking details.
- Prefer descriptive exception messages without sensitive data.
- Use guard clauses for invalid input; return 4xx vs 5xx appropriately in web filters.
- Do not swallow exceptions; log and rethrow domain-specific errors.

## Logging & Secrets

- **Never log raw tokens or credentials.**
- When logging, redact token values and PII; use `SecurityHelpers` utilities where available.
- Avoid println/System.out; use Spring `LoggerFactory` if needed.
- Keep debug logging off by default in library code.

## Security Invariants

- Auth header keys are API contracts; changing them requires migration notes.
- Run `IdentifierPolicy` before database queries to validate identifiers.
- Preserve token rotation semantics (batch windows, staleness); adjust tests if logic changes.
- Ensure `AuthenticationBypassDecider` covers public routes to prevent lockouts.
- Tokens BCrypt-hashed before storage (never plaintext).
- Token comparison results cached (Caffeine) for 1hr to reduce BCrypt overhead.
- Grace period allows batch requests without token thrashing.
- Sub-tokens scoped to parent APP token.
- Bearer token size validated (8KB default) to prevent memory exhaustion.
- Authentication cookies cleared on 401 to prevent stale-cookie loops (OWASP compliant).

## Testing Guidelines

- Framework: JUnit 5 with Spring test utilities.
- Location: `src/test/kotlin/<package>/<Name>Test.kt`.
- Naming: Backticked descriptive test names: `` `should rotate token outside batch window` ``.
- Prefer in-memory fakes (`InMemoryTokenRepository`) over real databases.
- When modifying token logic, add/adjust `AuthHeader` serialization tests.
- If changing schemas/resources, sync persistence tests and bundled SQL under `ogiri/db`.
- Coverage generated at `ogiri-core/build/reports/jacoco/test/html/index.html`.
- Keep tests deterministic; avoid system clock drift by using fixed `Instant` when needed.

## Performance Optimizations

- Token prefix indexing: 8-char prefix enables O(1) DB lookups vs O(n) BCrypt scans.
- Batch request caching: Recent timestamps cached to avoid DB queries for batch detection.
- Conditional cleanup: Token cleanup only runs when count exceeds 80% of `max-clients`.
- Batched deletion: Cleanup job deletes tokens in configurable batches to avoid DB overload.
- Sub-token registry caching: Registry lookups cached at service initialization.

## Configuration & Properties

- Config keys prefixed with `ogiri.*`; defaults documented in code and docs.
- Do not rename headers or property names without updating docs and tests.
- Keep `OgiriConfigurationProperties` defaults synchronized with docs in `docs/configuration.md`.

## Dependency & Version Management

- Version source: `.ogiri-version` (overridable via `RELEASE_VERSION` env or gradle prop).
- Do not bump dependency versions unless requested; BOM from Spring Boot 3.5.7.
- Security scanning via OWASP dependency-check is present; avoid disabling.

## Persistence & Interface-First Design

- Library is database-agnostic; do not add module-specific persistence to `ogiri-core`.
- For extensions, implement `OgiriTokenRepository` and `OgiriToken` or extend `OgiriBaseToken` per docs.
- Sub-token additions require `OgiriSubTokenRegistration` bean and tests in `TokenServiceSubTokenTest`.

## Key Extension Points

1. Custom Token Implementation: Extend `OgiriToken` interface
2. Custom User Directory: Implement `OgiriUserDirectory` SPI
3. Custom Sub-tokens: Implement `OgiriSubTokenRegistration`
4. Custom Routes: Implement `OgiriRouteRegistry`
5. Custom Token Service: Extend `OgiriTokenService<T>`

## Web Layer

- `OgiriTokenAuthenticationFilter` is central; keep request lifecycle stable.
- Ensure bypass logic via `AuthenticationBypassDecider.canSkip()` stays accurate for public routes.
- Avoid adding new filters without documenting ordering requirements.

## Documentation

- Docs live in `docs/`; prefer updating relevant guide when changing behavior.
- Keep README command snippets consistent with AGENTS instructions.
- Do not add new top-level docs unless user requests.

## Git & Commits

- Use Conventional Commits (feat/fix/refactor/docs/test/chore).
- Run `./gradlew build spotlessCheck` before pushing when feasible.
- Avoid force pushes; do not change git config.
- Do not commit secrets or local env files.

## Branching / PRs

- Branch from `ori`; keep PRs focused.
- Include tests with behavior changes; note coverage impacts.
- Reference related issues in PR description when applicable.

## Licensing

- Spotless injects license header from `spotless.license.kt`; do not remove.
- Preserve existing license headers in Kotlin/Java files when editing.
- Do not add license headers to new files.

## Miscellaneous

- Avoid introducing new dependencies without approval; prefer existing libraries.
- Keep public APIs stable; document breaking changes explicitly.
- Prefer pure functions and side-effect isolation for token logic.
- Be concise in logs and errors; avoid leaking implementation details.
- Ask for clarification if behavior is ambiguous before implementing.
