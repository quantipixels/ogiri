# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- New features and enhancements in development

### Changed
- Breaking changes in development

### Deprecated
- Deprecated features in development

### Removed
- Removed features in development

### Fixed
- Bug fixes in development

### Security
- Security-related changes in development

## [1.0.2] - 2025-12-06

### Fixed
- **Test Infrastructure:** Fixed `InMemoryTokenRepository.save()` losing transient `plainToken` property during data class copy operations
  - Issue: Kotlin data class `.copy()` only preserves constructor properties, causing `plainToken` to reset to null
  - Solution: Explicitly restore `plainToken` after copying token instance
  - Impact: Resolved 12 NullPointerException failures in TokenServiceSubTokenTest
- **Test Assertions:** Fixed type mismatch in token type comparisons
  - Issue: Tests compared String tokenType directly to TokenType enum values
  - Solution: Convert string to enum using `TokenType.of()` before assertion
  - Impact: All 20 tests now passing

### Changed
- Updated GitHub Actions badge URLs to use correct repository name (`mosobande/ogiri`)
- Updated POM metadata URLs to point to correct GitHub repository
- Updated contributor documentation with correct git clone URLs
- Configured JitPack support with `.jitpack.yml` for alternative Maven dependency access
- Added Yoruba tone marks to project name in README (Òǵìrì)

### Security
- Verified GitHub Actions credentials are properly scoped (OSSRH_USERNAME, OSSRH_PASSWORD, GPG keys)
- Confirmed no hardcoded secrets in workflow configuration files

### Enhancement
- Configure spotless formatter across all modules
- Centralise version management in `.ogiri-version`

## [1.0.1] - 2025-12-05

### Added
- Centralized version management in settings.gradle.kts
- CHANGELOG.md for release tracking
- SECURITY.md for vulnerability disclosure policy
- CONTRIBUTING.md for community contribution guidelines
- CODE_OF_CONDUCT.md for community standards
- .github/dependabot.yml for automated dependency updates
- OPTIMIZATION_REPORT.md documenting all improvements

### Changed
- Enhanced docs/DATABASE.md with schema file references
- Improved .github/workflows/lint.yml error messaging
- Externalized database credentials in sample applications

### Fixed
- Hardcoded PostgreSQL credentials now use environment variables
- Lint workflow now provides better error guidance
- Database configuration now follows 12-factor app principles

### Security
- Established vulnerability disclosure process (24-hour SLA)
- Added automated dependency scanning with Dependabot
- Externalized sensitive credentials from version control
- Created comprehensive security policy

## [1.0.0] - 2025-12-05

### Added
- Initial public release
- Token-based authentication with pluggable sub-tokens
- Database-agnostic `TokenRepository<T>` interface
- Spring Boot auto-configuration with `OgiriSecurityAutoConfiguration`
- Filter-based authentication via `OgiriTokenAuthenticationFilter`
- Configurable token rotation policies with grace periods
- Support for custom sub-tokens (chat, device, etc.) via `SubTokenRegistration`
- Comprehensive authentication header support with token serialization

### Database Support
- **SQL:** PostgreSQL, MySQL/MariaDB, Oracle, SQL Server, H2 (in-memory)
- **NoSQL:** MongoDB, Redis, DynamoDB, Cassandra, Firebase
- **Legacy:** Adapter pattern for existing token tables
- Bundled schema files for PostgreSQL, MySQL, and H2
- MongoDB collection setup with schema validation and TTL indexes

### Documentation
- Comprehensive README.md with database integration summary
- 460+ line Database Integration Guide covering 5 implementation patterns
- Authentication flow and token rotation documentation
- Sample applications in both Java and Kotlin
- Database-specific setup guides (PostgreSQL, MySQL, H2, MongoDB)
- Development guidelines (CLAUDE.md)

### CI/CD
- GitHub Actions workflows for build, test, lint, and release
- Automated Maven Central publishing with GPG signing
- Automatic snapshot deployments on main branch
- Tag-based release triggering (v*.*.*) for production releases
- Code formatting validation with Spotless
- Test coverage reporting to Codecov

### Sample Applications
- Pure Java sample application (zero Kotlin required)
- Kotlin Spring Boot sample application
- Both demonstrate required SPI implementations:
  - `TokenUserDirectory` interface
  - `RouteRegistry` interface
  - `TokenRepository<T>` interface

### Quality Assurance
- Full JUnit 5 test suite with comprehensive coverage
- Filter integration tests
- Token service functionality tests
- Sub-token management tests
- Header serialization validation
- Java 17 target (both compile and runtime)
- Kotlin 2.0.21 compatibility with Spring Boot 3.5.7

### No External Dependencies
- Zero forced database dependencies
- Applications control their own database drivers
- Independent choice of migration tools (Flyway, Liquibase, custom)
- Connection pooling decisions left to applications

---

## Upgrade Guide

### From Pre-Release to 1.0.0

No breaking changes. All existing APIs remain stable:
- `TokenRepository<T>` interface unchanged
- `BaseToken` entity fully compatible
- Authentication filter behavior consistent
- Auto-configuration remains optional

For detailed migration information, see [MIGRATION.md](./MIGRATION.md).

---

## Future Roadmap

### Planned for 1.1.0
- R2DBC support for reactive SQL databases
- Spring Data JDBC integration examples
- GraphQL authentication guide
- Multi-tenant token management patterns

### Planned for 2.0.0
- async/await token operations
- Enhanced token analytics and monitoring
- Token delegation and impersonation
- Rate limiting and token quota enforcement

---

## Contributing

We welcome contributions! For guidelines on contributing, see [CONTRIBUTING.md](./CONTRIBUTING.md) (coming soon).

## Security

For security issues and responsible disclosure, please see [SECURITY.md](./SECURITY.md) (coming soon).

## License

Apache License 2.0 - See LICENSE file for details
