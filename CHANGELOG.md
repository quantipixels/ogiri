# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.0.0] - 2026-02-27 (ogiri-security server)

### Breaking Changes

- **`OgiriTokenService` optional collaborators moved to setter injection** — the three optional constructor parameters (`auditHook`, `rateLimitHook`, `lookupCache`) and `@JvmOverloads` have been removed. The constructor now accepts only the six required collaborators. Optional collaborators are wired post-construction via `setAuditHook()`, `setRateLimitHook()`, and `setLookupCache()`.

  **Before (v2.x):**

  ```kotlin
  class MyTokenService(
      repository: OgiriTokenRepository<MyToken>,
      ...,
      lookupCache: OgiriTokenLookupCache<MyToken>? = null,
  ) : OgiriTokenService<MyToken>(repository, ..., lookupCache = lookupCache)
  ```

  **After (v3.x):**

  ```kotlin
  // Concrete repository type works — Spring resolves via ResolvableType
  class MyTokenService(
      repository: MyTokenRepository,
      passwordEncoder: PasswordEncoder,
      userDirectory: OgiriUserDirectory,
      identifierPolicy: IdentifierPolicy,
      subTokenRegistry: OgiriSubTokenRegistry,
      properties: OgiriConfigurationProperties,
  ) : OgiriTokenService<MyToken>(
      repository, passwordEncoder, userDirectory,
      identifierPolicy, subTokenRegistry, properties,
  )
  ```

  Optional beans (`OgiriAuditHook`, `OgiriRateLimitHook`, `OgiriTokenLookupCache`) are wired automatically by the ogiri auto-configuration via setter injection when the corresponding beans are present in the application context. To wire them explicitly in a `@Bean` factory:

  ```kotlin
  @Bean
  fun myTokenService(...): MyTokenService {
      val service = MyTokenService(repository, ...)
      service.setLookupCache(myCache)
      return service
  }
  ```

### Added

- **`NoOpOgiriAuditHook`** — public singleton object in `spi` package; default no-op for `OgiriAuditHook`. Useful for explicitly resetting the hook in tests (`service.setAuditHook(NoOpOgiriAuditHook)`).
- **`NoOpOgiriRateLimitHook`** — public singleton object in `spi` package; default no-op for `OgiriRateLimitHook`.

### Changed

- `OgiriTokenService` — `open` setters `setAuditHook()`, `setRateLimitHook()`, `setLookupCache()` added for post-construction injection of optional collaborators, following the Spring Security 6.x `AuthorizationFilter` pattern
- `OgiriSecurityAutoConfiguration` — factory method uses `ObjectProvider.ifAvailable { service.setX(it) }` after constructing the service
- All sample services (Kotlin + Java, JPA + JDBC) updated to 6-arg constructors with concrete repository types

---

## [2.1.0] - 2026-02-24 (ogiri-security server)

### Breaking Changes

- **`OgiriTokenService` constructor signature changed** — the three trailing `ObjectProvider<Hook>` parameters (`auditHookProvider`, `rateLimitHookProvider`, `lookupCacheProvider`) are replaced by nullable direct references (`auditHook: OgiriAuditHook? = null`, `rateLimitHook: OgiriRateLimitHook? = null`, `lookupCache: OgiriTokenLookupCache<T>? = null`).

  **Kotlin subclasses** — replace `ObjectProvider` params with nullable params and pass via named argument:

  ```kotlin
  // Before
  class MyTokenService(..., auditHookProvider: ObjectProvider<OgiriAuditHook>, ...) :
      OgiriTokenService<MyToken>(..., auditHookProvider, rateLimitHookProvider, lookupCacheProvider)

  // After
  class MyTokenService(..., lookupCache: OgiriTokenLookupCache<MyToken>? = null) :
      OgiriTokenService<MyToken>(..., lookupCache = lookupCache)
  ```

  **Java subclasses** — pass `null` explicitly for unused optional params (named-argument syntax unavailable in Java):

  ```java
  // Before
  super(..., auditHookProvider, rateLimitHookProvider);

  // After
  super(..., /* auditHook */ null, /* rateLimitHook */ null, lookupCache);
  ```

  Auto-configuration is unaffected — it still resolves hooks via Spring `ObjectProvider` and passes the resolved nullable values to the constructor.

### Added

- **`ogiri-jdbc` module** — Spring `JdbcClient`-based token repository adapter; drop-in alternative to JPA with no ORM overhead
- **`ogiri-caffeine` module** — optional in-process Caffeine lookup cache for `OgiriTokenLookupCache` SPI
- **`ogiri-redis` module** — optional distributed Redis lookup cache for `OgiriTokenLookupCache` SPI
- **`OgiriTokenLookupCache` SPI** — pluggable cache interface wired into `OgiriTokenService`; custom beans auto-detected via Spring
- **JDBC sample profile** — `sample/` now includes a JDBC Spring profile alongside existing JPA examples
- `ogiri-caffeine` dependency added to both Java and Kotlin sample apps

### Changed

- `OgiriTokenService` constructor annotated with `@JvmOverloads` so Java subclasses can omit trailing optional parameters without needing to pass all nine arguments explicitly
- `scripts/release.sh` now reads the current `.ogiri-version`, shows the pending version change in the confirmation prompt, and writes the new value after the user confirms — no manual file edit needed when passing `--version`

### Documentation

- Comprehensive KDoc pass across `ogiri-core` and `ogiri-jdbc` — public API contracts, SPI hook parameters, and JDBC module usage examples
- `plainToken` documented as `NEVER logged` (in addition to never persisted) on both `OgiriToken` and `OgiriBaseToken`
- `(userId, client)` pair documented as a single-writer key to prevent unnecessary locking machinery being added in future
- Developer quick-start, TypeScript client setup, and tool prerequisites consolidated into `README.md`; `docs/agents/` directory and per-sample `AGENTS.md` files removed

## [2.0.0] - 2026-02-10 (ogiri-security-client)

### Breaking

- `OgiriClient` removed, replaced by `OgiriAuth` (auth primitives) + `OgiriFetchClient` (optional fetch wrapper)
- Config split: `OgiriAuthConfig` (for `OgiriAuth`) vs `OgiriClientConfig` (for `OgiriFetchClient`)

### Added

- `ogiri-security-client/axios` sub-entrypoint with `createAxiosInterceptors(auth)` for axios adapter
- `OgiriAuth.subscribe()` for auth state change listeners
- `OgiriAuth.headerInjector()` for BYO HTTP clients
- npm publish workflow in `release.yml`

### Changed

- oxlint/oxfmt replaces manual formatting

## [1.4.1] - 2026-01-14

### Security Improvements

- **Authentication cookies cleared on 401 responses**: When cookie authentication is enabled (`ogiri.cookies.enabled=true`), the authentication entry point now clears all authentication cookies (access-token, client, uid, expiry) on 401 Unauthorized responses. This prevents clients from being stuck in a 401 loop with stale HttpOnly cookies and aligns with OWASP session management best practices.

### Changed

- `OgiriAuthenticationEntryPoint` now requires `OgiriConfigurationProperties` dependency to access cookie configuration

### Added

- Cookie clearing logic in `OgiriAuthenticationEntryPoint.clearAuthCookies()` method
- Comprehensive test suite for 401 cookie clearing behavior (`OgiriAuthenticationEntryPointTest`)
- Debug logging when authentication cookies are cleared

## [1.4.0] - 2026-01-08

### Breaking Changes

- **Default token length increased** from 16 to 32 characters for improved security (24 chars hidden entropy after 8-char prefix)
- **Default `rotateStaleSeconds` changed** from 0 (disabled) to 3600 (1 hour) - secure by default
- **Removed `deleteExpiredBatch`** from `OgiriTokenRepository` interface (now internal to service implementation)
- **Removed `-Xjvm-default=all`** compiler flag requirement (no longer needed for consuming projects)

### Security Fixes

- **Fixed token cache timing attack**: Added constant-time delay (100ms minimum) to mask cache hit vs miss timing differences
- **Hardened cache keys**: Cache keys now use SHA-256 hashes instead of plaintext tokens to prevent extraction from memory dumps
- **Fixed user enumeration timing attack**: Added constant-time dummy password check when user not found to prevent timing-based username enumeration
- **Increased default token length**: Changed from 16 to 32 characters to provide 24 chars of hidden entropy (after 8-char prefix)
- **Enabled token rotation by default**: Changed default `rotateStaleSeconds` from 0 (disabled) to 3600 (1 hour) for periodic credential refresh
- **Added path traversal attack logging**: Log warnings when URI parsing fails during bypass check to detect potential path traversal attempts

### Performance Improvements

- **Added batch token fetching**: New `findByUserIdAndClientIn` method enables single query for all sub-tokens, eliminating N+1 query problem in `buildAuthHeader`
- **Added prefix fallback warning**: Log warning when token prefix lookup returns no candidates and falls back to full table scan
- **Optimized `countByUserId`**: Sample repositories now use `COUNT(*)` query instead of loading all tokens

### Repository Simplification

- Removed `-Xjvm-default=all` compiler flag from `ogiri-core` and `sample-kotlin` build scripts
- Moved batch cleanup logic from repository interface to service implementation
- Java and Kotlin samples now have symmetric implementations
- Cleaner separation between repository concerns and service orchestration

### Documentation

- Updated CHANGELOG with comprehensive 1.4.0 release notes
- Updated security default warnings in configuration properties
- Improved inline documentation for new security features

## [1.3.1] - 2026-01-08

### Changed

- **Simplified Repository Integration**: `OgiriTokenRepository` method names now follow Spring Data naming conventions, enabling direct interface extension without requiring the adapter pattern.

  - Users can now simply extend both `JpaRepository` (or `CrudRepository`) and `OgiriTokenRepository` in a single interface
  - Spring Data automatically generates all query implementations
  - Reduces boilerplate from 3 files (~65 lines) to 2 files (~15 lines)

- **Method Renames** (Spring Data compatible naming):

  - `findAllByUserId()` → `findByUserIdOrderByUpdatedAtDesc()`
  - `findAllByUserIdAndTokenSubtype()` → `findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc()`
  - `findAllByTokenType()` → `findByTokenType()`
  - `findValidTokensByPrefix()` → `findByTokenPrefixAndTokenTypeAndExpiryAtAfter()`

- **Return Type Changes** (Java-friendly API):
  - `findById()` now returns `Optional<T>` instead of `T?`
  - `findByUserIdAndClient()` now returns `Optional<T>` instead of `T?`

### Deprecated

- `AbstractJpaTokenRepositoryAdapter` in `ogiri-jpa` module is now deprecated. Use direct interface extension instead.

### Migration Guide

**Before (1.3.0 and earlier):**

```kotlin
// Required 3 files: Entity + JPA Repository + Adapter
interface MyTokenJpaRepository : JpaRepository<MyToken, Long> {
    fun findByUserIdAndClient(userId: Long, client: String): MyToken?
    // ... many more methods
}

class MyTokenRepositoryAdapter(
    private val jpaRepository: MyTokenJpaRepository
) : AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(jpaRepository)
```

**After (1.3.1+):**

```kotlin
// Only 2 files: Entity + Repository Interface
@Repository
interface MyTokenRepository :
    JpaRepository<MyToken, Long>,
    OgiriTokenRepository<MyToken>
```

**Method call updates (if calling repository directly):**

```kotlin
// Old
repository.findAllByUserId(userId)
repository.findByUserIdAndClient(userId, client)  // returned T?

// New
repository.findByUserIdOrderByUpdatedAtDesc(userId)
repository.findByUserIdAndClient(userId, client).orElse(null)  // returns Optional<T>
```

## [1.3.0] - 2025-01-08

### Added

- **New `ogiri-jpa` Module**: Dedicated JPA/Hibernate support module that reduces boilerplate by ~70%
  - `OgiriBaseTokenEntity`: `@MappedSuperclass` with all 15+ token fields pre-annotated
  - `AbstractJpaTokenRepositoryAdapter`: Base adapter eliminating ~80 lines of repository boilerplate
  - `OgiriJpaAutoConfiguration`: Spring Boot auto-configuration for the JPA module
- **PasswordEncoder Auto-Configuration**: Automatically provides `BCryptPasswordEncoder` when no `PasswordEncoder` bean exists
- **OgiriMissingBeanFailureAnalyzer**: Helpful error messages when required beans are missing, with specific guidance for each bean type
- **Configuration Processor**: IDE autocomplete support for `ogiri.*` properties
- Token prefix indexing for O(1) database lookups (performance optimization)
- Configurable `max-bearer-token-size` property (default 8192, DoS protection)
- Configurable `cleanup.batch-size` property for batched token deletion
- Cookie configuration properties (`enabled`, `secure`, `http-only`, `same-site`, `path`)
- Startup warnings for insecure configurations
- New repository methods: `findValidTokensByPrefix`, `countByUserId`, `deleteExpiredBatch`

### Changed

- **Sample Applications**: Updated to use `ogiri-jpa` module, demonstrating the simplified approach
- **Documentation**: Restructured to highlight `ogiri-jpa` as the recommended path for JPA users
- **CI Workflows**: Updated to build, test, and publish the new `ogiri-jpa` module
- Token cleanup now uses batched deletion for large datasets
- Bearer token size validation is now configurable

### Installation

**With JPA Support (Recommended):**

```kotlin
implementation("com.quantipixels.ogiri:ogiri-jpa:1.3.0")
```

**Core Only (Custom Persistence):**

```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.3.0")
```

### Database Migration (Optional)

For optimal performance, add `token_prefix` column:

- Add `token_prefix VARCHAR(8)` column to tokens table
- Add index on `token_prefix` (partial index for PostgreSQL recommended)

See [Migration Guide](migration-guide.md#migrating-to-130) for detailed upgrade instructions.

### Upgrade Notes

- This release is fully backward compatible
- Existing `ogiri-core` users can continue without changes
- JPA users are encouraged to migrate to `ogiri-jpa` for reduced boilerplate (see [Migration Guide](migration-guide.md))

## [1.2.1] - 2025-01-07

### Added

- Comprehensive test suite for token and security services
- Initial secure cookie support with secure defaults (enhanced with properties in 1.3.0)
- Token cleanup optimization with batched deletion
- Fallback error messages in OgiriAuthenticationEntryPoint to prevent 500 errors when messages.properties is missing

### Changed

- Renamed SubTokenRegistry to OgiriSubTokenRegistry for consistency
- OgiriRouteRegistry method renamed from `registrations()` to `routes()`

### Fixed

- Bearer token authentication now returns 401 with proper error message instead of 500

## [1.2.0] - 2025-12-12

### Added

- **Interface-First Design**: Introduced `OgiriToken` interface as the primary contract for tokens, allowing implementation flexibility without forced inheritance.
- **Migration Guide**: Added [Migration Guide](migration-guide.md) to assist with the transition to 1.2.0.

### Changed

- **Renamed Core Classes**:
  - `BaseToken` → `OgiriBaseToken`
  - `TokenRepository` → `OgiriTokenRepository`
  - `TokenService` → `OgiriTokenService`
  - `GeneratedTokens` → `OgiriGeneratedTokens`
- **Updated Type Bounds**: All generic type parameters now use `<T : OgiriToken>` instead of `<T : BaseToken>`.
- **Documentation**: Comprehensive updates to reflect the new interface-first architecture.

### Upgrade Notes

- This release involves renaming core classes. Please refer to the [Migration Guide](migration-guide.md) for detailed instructions on updating your code.

## [1.1.1] - 2025-12-11

### Added

- GitHub Pages automatic deployment workflow with mike versioning
- Documentation versioning support with multi-version dropdown
- Documentation improvements and standardization
- `scripts/publish-docs.sh` for manual documentation deployment
- `docs/versioning-guide.md` for comprehensive versioning documentation
- `docs/github-pages-setup.md` deployment and setup guide

### Changed

- **Documentation restructured** - Removed 60% duplication
  - Consolidated CLAUDE.md into AGENTS.md (600 → 200 lines)
  - Simplified README.md (342 → 97 lines)
  - Merged redundant files (release.md, migration.md, getting-started.md)
- Standardized all documentation filenames to lowercase with hyphens
- Updated mkdocs.yml with mike versioning provider
- Enhanced .github/workflows/docs.yml with three-job deployment pipeline
- Improved AGENTS.md as comprehensive AI assistant guidance (from CLAUDE.md)

### Fixed

- All documentation cross-references verified (0 broken links)
- Consistent file naming across docs directory
- GitHub Pages configuration for automatic deployments

### Documentation Quality

- 11 focused core docs (down from 13)
- Single source of truth for all topics
- 5-minute quickstart guide for new users
- Clear information hierarchy
- Professional Material theme configuration

## [1.1.0] - 2025-12-09

### Added

- `OgiriUser.getOgiriUserId()` for conflict-free Java getter while retaining Kotlin property access
- Documentation for overriding auto-configured `OgiriTokenAuthenticationFilter` with custom bean/subclass
- Java sample repository with `findAllByUserIdAndTokenSubtype(...)` for sub-token queries
- `parseBearerToken()` with fallback in auth filter for Base64/JSON bearer payloads
- `OgiriSubTokenRegistration.validate()` plus `OgiriTokenService` helpers (`getSubToken`, `revokeSubToken`, `renewSubTokenAndGetHeaders`)

### Changed

- Replaced deprecated `NoOpPasswordEncoder` in tests with inline `PasswordEncoder` stub
- Token service and filters now consistently call `user.getOgiriUserId()`
- `scripts/release.sh` supports `-f/--force` for reusing/overwriting existing tags

### Fixed

- Java sample `SampleOgiriUserDirectory.SampleUser` now implements `getOgiriUserId()`

### Upgrade Notes

If upgrading from 1.0.x:

1. **Update version** - Change dependency to `1.1.0`
2. **Implement `getOgiriUserId()`** - Java implementations must add `long getOgiriUserId()` method
3. **Add `findAllByUserIdAndTokenSubtype()`** - Required in `OgiriTokenRepository` for sub-token rotation
4. **Review filter overrides** - If you register `OgiriTokenAuthenticationFilter`, auto-configuration will use your bean

## [1.0.4] - 2025-12-08

### Fixed

- Removed conflicting `jitpack.yml` configuration

## [1.0.3] - 2025-12-08

### Fixed

- Fixed `.jitpack.yml` configuration for package usage
- Fixed Spotless breaking build
- Fixed tests failing in GitHub Actions

## [1.0.2] - 2025-12-06

### Fixed

- **Test Infrastructure:** Fixed `InMemoryTokenRepository.save()` losing transient `plainToken` property during data class copy operations
- **Test Assertions:** Fixed type mismatch in token type comparisons (String vs TokenType enum)

### Changed

- Updated GitHub Actions badge URLs to correct repository name (`mosobande/ogiri`)
- Updated POM metadata URLs to point to correct GitHub repository
- Configured JitPack support with `.jitpack.yml`
- Configured Spotless formatter across all modules
- Centralized version management in `.ogiri-version`

### Security

- Verified GitHub Actions credentials are properly scoped

## [1.0.1] - 2025-12-05

### Added

- Centralized version management in `settings.gradle.kts`
- changelog.md, security.md, contributing.md, code-of-conduct.md
- `.github/dependabot.yml` for automated dependency updates

### Changed

- Enhanced `docs/database.md` with schema file references
- Improved `.github/workflows/lint.yml` error messaging
- Externalized database credentials in sample applications

### Fixed

- Hardcoded PostgreSQL credentials now use environment variables
- Lint workflow provides better error guidance

### Security

- Established vulnerability disclosure process (24-hour SLA)
- Added automated dependency scanning with Dependabot
- Created comprehensive security policy

## [1.0.0] - 2025-12-05

### Added

- Initial public release
- Token-based authentication with pluggable sub-tokens
- Database-agnostic `TokenRepository<T>` interface
- Spring Boot auto-configuration with `OgiriSecurityAutoConfiguration`
- Filter-based authentication via `OgiriTokenAuthenticationFilter`
- Configurable token rotation policies with grace periods
- Support for custom sub-tokens via `OgiriSubTokenRegistration`

### Database Support

- **SQL:** PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, H2
- **NoSQL:** MongoDB, Redis, DynamoDB, Cassandra, Firebase
- **Legacy:** Adapter pattern for existing token tables
- Bundled schema files for PostgreSQL, MySQL, and H2

### CI/CD

- GitHub Actions workflows for build, test, lint, and release
- Automated Maven Central publishing with GPG signing
- Automatic snapshot deployments on main branch
- Tag-based release triggering (`v*.*.*`)

### Sample Applications

- Pure Java sample application
- Kotlin Spring Boot sample application
- Both demonstrate required SPI implementations

## License

Apache License 2.0
