# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

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
