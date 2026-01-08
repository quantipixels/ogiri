# Plan: Update Sample Applications for ogiri-jpa

## Current State

### sample-kotlin

| File | Lines | Purpose |
|------|-------|---------|
| `entity/SampleToken.kt` | 96 | JPA entity with 15+ fields, all manually annotated |
| `repository/SampleTokenRepository.kt` | 114 | JPA repo implementing `OgiriTokenRepository` directly |
| `service/SampleTokenService.kt` | 105 | Custom TokenService with `tokenFactory()` |
| `config/SecurityConfig.kt` | 23 | PasswordEncoder bean |

**Total boilerplate:** ~340 lines

### Problem

Users must write:
1. ~95 lines for token entity with JPA annotations
2. ~115 lines for repository with query methods
3. ~105 lines for TokenService (mostly `tokenFactory()` override)
4. ~23 lines for PasswordEncoder

---

## After ogiri-jpa

### New sample-kotlin Structure

| File | Lines | Purpose |
|------|-------|---------|
| `entity/SampleToken.kt` | ~10 | Extends `OgiriBaseTokenEntity` |
| `repository/SampleTokenJpaRepository.kt` | ~25 | Pure JPA repository interface |
| `repository/SampleTokenRepositoryAdapter.kt` | ~30 | Extends `AbstractJpaTokenRepositoryAdapter` |
| `service/SampleTokenService.kt` | ~40 | Simplified (tokenFactory uses inherited entity) |
| `config/SecurityConfig.kt` | 0 | **DELETED** (PasswordEncoder auto-configured) |

**Total boilerplate:** ~105 lines (**~70% reduction**)

---

## Changes Required

### 1. Update build.gradle.kts

**Before:**
```kotlin
dependencies {
    implementation(project(":ogiri-core"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
}
```

**After:**
```kotlin
dependencies {
    implementation(project(":ogiri-jpa"))
    // ogiri-jpa transitively includes ogiri-core and spring-data-jpa
}
```

### 2. Simplify SampleToken Entity

**Before (96 lines):**
```kotlin
@Entity
@Table(name = "user_tokens", indexes = [...], uniqueConstraints = [...])
data class SampleToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    override val userId: Long = 0,

    // ... 13+ more fields with annotations
) : OgiriBaseToken()
```

**After (~10 lines):**
```kotlin
@Entity
@Table(
    name = "user_tokens",
    indexes = [
        Index(name = "idx_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_tokens_expiry", columnList = "expiry_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tokens_user_client", columnNames = ["user_id", "client"]),
    ],
)
class SampleToken : OgiriBaseTokenEntity()
```

All 15+ fields with annotations inherited from `OgiriBaseTokenEntity`.

### 3. Split Repository into JPA Interface + Adapter

**Before (114 lines in one file):**
```kotlin
@Repository
interface SampleTokenRepository :
    JpaRepository<SampleToken, Long>, OgiriTokenRepository<SampleToken> {

    // 20+ methods mixing JPA and OgiriTokenRepository
    override fun findAllByUserId(userId: Long): List<SampleToken> = ...
    override fun findByUserIdAndClient(...): SampleToken? = ...
    // ... many more overrides and @Query methods
}
```

**After (two files, ~55 lines total):**

**SampleTokenJpaRepository.kt (~25 lines):**
```kotlin
@Repository
interface SampleTokenJpaRepository : JpaRepository<SampleToken, Long> {
    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SampleToken>
    fun findByUserIdAndClient(userId: Long, client: String): Optional<SampleToken>
    fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<SampleToken>
    fun findByExpiryAtBefore(cutoff: Instant): List<SampleToken>

    @Modifying @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client = ?2")
    fun deleteByUserIdAndClient(userId: Long, client: String)

    @Modifying @Query("DELETE FROM SampleToken t WHERE t.userId = ?1 AND t.client IN ?2")
    fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>)

    @Modifying @Query("DELETE FROM SampleToken t WHERE t.userId = ?1")
    fun deleteByUserId(userId: Long)
}
```

**SampleTokenRepositoryAdapter.kt (~30 lines):**
```kotlin
@Repository
@Primary
class SampleTokenRepositoryAdapter(
    jpaRepository: SampleTokenJpaRepository
) : AbstractJpaTokenRepositoryAdapter<SampleToken, SampleTokenJpaRepository>(jpaRepository) {

    override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
        jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)

    override fun findByUserIdAndClientEquals(userId: Long, client: String) =
        jpaRepository.findByUserIdAndClient(userId, client).orElse(null)

    override fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String) =
        jpaRepository.findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype)

    override fun findByExpiryAtBeforeCutoff(cutoff: Instant) =
        jpaRepository.findByExpiryAtBefore(cutoff)

    override fun deleteByUserIdAndClientEquals(userId: Long, client: String) =
        jpaRepository.deleteByUserIdAndClient(userId, client)

    override fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>) =
        jpaRepository.deleteByUserIdAndClientIn(userId, clientIds)

    override fun deleteByUserIdJpa(userId: Long) =
        jpaRepository.deleteByUserId(userId)
}
```

### 4. Simplify SampleTokenService

**Before (~105 lines):**
```kotlin
@Service
@Primary
class SampleTokenService(
    private val sampleTokenRepository: SampleTokenRepository,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
) : OgiriTokenService<SampleToken>(...) {

    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: OgiriTokenType,
        expiry: Instant,
        tokenSubtype: String?,
        plainTokenValue: String,
    ): SampleToken {
        val token = SampleToken(
            id = 0,
            userId = userId,
            client = client,
            token = hashedToken,
            tokenType = tokenType.name,
            expiryAt = expiry,
            tokenSubtype = tokenSubtype,
        )
        token.plainToken = plainTokenValue
        return token
    }
}
```

**After (~40 lines):**
```kotlin
@Service
@Primary
class SampleTokenService(
    tokenRepository: OgiriTokenRepository<SampleToken>,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
) : OgiriTokenService<SampleToken>(...) {

    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: OgiriTokenType,
        expiry: Instant,
        tokenSubtype: String?,
        plainTokenValue: String,
    ): SampleToken {
        return SampleToken().apply {
            this.userId = userId
            this.client = client
            this.token = hashedToken
            this.tokenType = tokenType.name
            this.expiryAt = expiry
            this.tokenSubtype = tokenSubtype
            this.plainToken = plainTokenValue
        }
    }
}
```

### 5. Delete SecurityConfig.kt

PasswordEncoder is now auto-configured by `ogiri-core`. Remove the file entirely.

### 6. Update Tests

Tests should continue to work since the public API hasn't changed. Minor updates may be needed for:
- Import changes (if any class moves)
- Repository injection (adapter vs direct)

---

## Files to Modify

### sample-kotlin

| Action | File | Change |
|--------|------|--------|
| Modify | `build.gradle.kts` | Change `ogiri-core` to `ogiri-jpa` |
| Simplify | `entity/SampleToken.kt` | Extend `OgiriBaseTokenEntity` |
| Split | `repository/SampleTokenRepository.kt` | → `SampleTokenJpaRepository.kt` + `SampleTokenRepositoryAdapter.kt` |
| Simplify | `service/SampleTokenService.kt` | Use property assignment |
| Delete | `config/SecurityConfig.kt` | PasswordEncoder auto-configured |
| Verify | Tests | Ensure they still pass |

### sample-java (similar changes)

| Action | File | Change |
|--------|------|--------|
| Modify | `build.gradle.kts` | Change `ogiri-core` to `ogiri-jpa` |
| Simplify | `entity/SampleToken.java` | Extend `OgiriBaseTokenEntity` |
| Split | Repository | Same pattern as Kotlin |
| Simplify | Service | Same pattern as Kotlin |
| Delete | `SecurityConfig.java` | PasswordEncoder auto-configured |

---

## Implementation Order

1. **First:** Complete ogiri-jpa module (from main plan)
2. **Then:** Update sample-kotlin
3. **Finally:** Update sample-java

---

## Success Criteria

After changes:

1. Sample apps compile and all tests pass
2. Entity file reduced from ~95 lines to ~10 lines
3. Repository split into clean JPA interface + thin adapter
4. No manual PasswordEncoder bean required
5. Total sample boilerplate reduced by ~70%
