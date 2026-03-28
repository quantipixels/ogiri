# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [3.1.0] - 2026-03-28

### Security

- Replace truncated `DUMMY_HASH` constant with a runtime-generated BCrypt hash to close timing oracle on unknown-user login path

### Added

- JaCoCo coverage gates for `ogiri-jdbc` (85% baseline) and `ogiri-jpa` (0% baseline, no tests yet)
- Three new Redis integration tests covering cache miss, targeted eviction, and empty-keyspace `evictAll`
- Route authorization gap documented in `OgiriSecurityAutoConfiguration` KDoc and `docs/quickstart.md`

### Changed

- Sub-bearer logic extracted from `OgiriTokenService` into `SubTokenService` (delegation, no API change)
- `OgiriBaseTokenEntity.expiryAt` is now a required constructor parameter (no silent `Instant.now()` default)
- Testcontainers pinned to 1.20.4 in `ogiri-redis` to restore Docker Desktop 29.x compatibility

---

## [3.0.1] - 2026-03-01

### Fixed

- Redis deserialization: `JavaTimeModule` now registered via `configure {}` without replacing the ObjectMapper's polymorphic type resolver

### Removed

- `ogiri-client` TypeScript package (already retired; auth primitives are inlined in the React sample)

---

## [3.0.0] - 2026-02-27

### Breaking

- `OgiriTokenService` optional collaborators (`auditHook`, `rateLimitHook`, `lookupCache`) moved from constructor params to setter injection (`setAuditHook()`, `setRateLimitHook()`, `setLookupCache()`). The constructor now requires only the six mandatory collaborators.

### Added

- `NoOpOgiriAuditHook` and `NoOpOgiriRateLimitHook` — public singleton no-ops for use in tests and explicit resets

---

## [2.1.0] - 2026-02-24

### Breaking

- `OgiriTokenService` constructor `ObjectProvider<Hook>` params replaced with nullable direct references (`auditHook: OgiriAuditHook? = null`, etc.)

### Added

- `ogiri-jdbc` — Spring `JdbcClient`-based token repository adapter
- `ogiri-caffeine` — optional in-process Caffeine lookup cache
- `ogiri-redis` — optional distributed Redis lookup cache
- `OgiriTokenLookupCache` SPI wired into `OgiriTokenService`

---

## [2.0.0] - 2026-02-10 (ogiri-security-client)

### Breaking

- `OgiriClient` removed; replaced by `OgiriAuth` (auth primitives) and `OgiriFetchClient` (optional fetch wrapper)

### Added

- `ogiri-security-client/axios` sub-entrypoint with `createAxiosInterceptors(auth)`
- `OgiriAuth.subscribe()` for auth state listeners
- `OgiriAuth.headerInjector()` for BYO HTTP clients

---

## [1.4.1] - 2026-01-14

### Fixed

- Auth cookies cleared on 401 responses when `ogiri.cookies.enabled=true`, preventing stale-cookie 401 loops

---

## [1.4.0] - 2026-01-08

### Breaking

- Default token length increased from 16 to 32 characters
- Default `rotateStaleSeconds` changed from 0 to 3600 (secure by default)
- `deleteExpiredBatch` removed from `OgiriTokenRepository` (now internal to the service)

### Security

- Cache keys now use SHA-256 hashes instead of plaintext tokens
- Constant-time dummy password check added to `verifyUser` to prevent user-enumeration via timing

### Changed

- Batch token fetching via `findByUserIdAndClientIn` eliminates N+1 queries in `buildAuthHeader`

---

## [1.3.1] - 2026-01-08

### Breaking

- `OgiriTokenRepository` method names aligned to Spring Data conventions (e.g. `findAllByUserId` → `findByUserIdOrderByUpdatedAtDesc`); `findByUserIdAndClient` now returns `Optional<T>`

### Deprecated

- `AbstractJpaTokenRepositoryAdapter` — use direct interface extension instead

---

## [1.3.0] - 2025-01-08

### Added

- `ogiri-jpa` module: `OgiriBaseTokenEntity` (`@MappedSuperclass`), `AbstractJpaTokenRepositoryAdapter`, and `OgiriJpaAutoConfiguration`
- `BCryptPasswordEncoder` auto-configuration when no `PasswordEncoder` bean is present
- `OgiriMissingBeanFailureAnalyzer` for actionable startup errors

---

## [1.2.1] - 2025-01-07

### Added

- Secure cookie support and batched token cleanup

### Fixed

- Bearer token auth returns 401 instead of 500 when authentication fails

---

## [1.2.0] - 2025-12-12

### Breaking

- Core classes renamed: `BaseToken` → `OgiriBaseToken`, `TokenRepository` → `OgiriTokenRepository`, `TokenService` → `OgiriTokenService`, `GeneratedTokens` → `OgiriGeneratedTokens`

---

## [1.1.1] - 2025-12-11

### Added

- GitHub Pages deployment with `mike` versioning

---

## [1.1.0] - 2025-12-09

### Added

- `OgiriUser.getOgiriUserId()` for conflict-free Java interop
- `OgiriSubTokenRegistration.validate()` and sub-token helpers (`getSubToken`, `revokeSubToken`, `renewSubTokenAndGetHeaders`)

---

## [1.0.0] - 2025-12-05

### Added

- Initial release: token authentication, sub-token support, Spring Boot auto-configuration, JPA/JDBC/NoSQL repository interface, configurable token rotation

## License

Apache License 2.0
