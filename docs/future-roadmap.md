# Future Roadmap

Items identified during security audit (2026-02-08) that are worth doing but not urgent.

---

## R1: ogiri-jpa Default Repository

Provide an intermediate JPA repository with optimized `@Query` annotations so users don't have to write boilerplate overrides for `countByUserId`, `deleteByExpiryAtBefore`, etc.

```kotlin
@NoRepositoryBean
interface OgiriJpaTokenRepository<T : OgiriBaseTokenEntity> :
    JpaRepository<T, Long>, OgiriTokenRepository<T> {

    @Modifying @Query("DELETE FROM #{#entityName} t WHERE t.expiryAt < ?1")
    override fun deleteByExpiryAtBefore(cutoff: Instant): Int

    @Query("SELECT COUNT(t) FROM #{#entityName} t WHERE t.userId = ?1")
    override fun countByUserId(userId: Long): Long
}
```

Users would extend `OgiriJpaTokenRepository<MyToken>` instead of both `JpaRepository` and `OgiriTokenRepository` separately. Eliminates boilerplate and ensures optimal queries.

**Impact**: Reduces user-side code, prevents N+1 mistakes
**Effort**: Low — single interface file in ogiri-jpa module

---

## R2: Test Coverage Gaps

Audit issue 13 identified missing test coverage areas. Not blocking but should be addressed:

- Edge cases in token rotation during concurrent requests
- Sub-token lifecycle (create, validate, revoke, renew) end-to-end
- Cleanup job behavior under load
- Error paths in `verifyUser` (rate limiting, audit hooks)

**Impact**: Prevents regressions as the codebase evolves
**Effort**: Medium

---

## R3: Migration Guide [COMPLETED]

Completed in 2.0.0 docs overhaul. The migration guide now covers all version upgrades with breaking changes, schema migration SQL, and before/after code examples.
