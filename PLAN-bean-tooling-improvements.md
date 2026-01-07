# Plan: Bean Tooling Improvements for Ogiri

## Problem Statement

Currently, integrating Ogiri requires significant boilerplate:
1. ~80 lines of adapter code for `OgiriTokenRepository` (JPA)
2. Manual token entity with 15+ fields
3. No IDE autocomplete for `ogiri.*` properties
4. No helpful error messages when required beans are missing
5. Users must manually configure `PasswordEncoder`

## Decisions

- **JPA Support:** Separate `ogiri-jpa` module (not in core)
- **Future Adapters:** Roadmap for `ogiri-mongo`, `ogiri-redis`
- **Scope:** All 6 improvements

---

## Module Architecture

```
ogiri/
├── ogiri-core/                    # Core library (existing)
│   └── No database dependencies
│
├── ogiri-jpa/                     # NEW: JPA adapter module
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── com/quantipixels/ogiri/jpa/
│           ├── OgiriJpaAutoConfiguration.kt
│           ├── AbstractJpaTokenRepositoryAdapter.kt
│           └── OgiriBaseTokenEntity.kt
│
├── ogiri-mongo/                   # FUTURE: MongoDB adapter
└── ogiri-redis/                   # FUTURE: Redis adapter
```

### How Modules Work Together

```
┌─────────────────────────────────────────────────────────────┐
│                      User Application                        │
├─────────────────────────────────────────────────────────────┤
│  @Entity                                                     │
│  class MyToken : OgiriBaseTokenEntity()  ◄─── extends       │
│                                                              │
│  @Repository                                                 │
│  class MyTokenRepo(jpa) :                                   │
│      AbstractJpaTokenRepositoryAdapter<MyToken>(jpa)        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       ogiri-jpa                              │
├─────────────────────────────────────────────────────────────┤
│  • OgiriBaseTokenEntity (@MappedSuperclass)                 │
│  • AbstractJpaTokenRepositoryAdapter<T>                     │
│  • OgiriJpaAutoConfiguration                                │
│                                                              │
│  Dependencies:                                               │
│    - ogiri-core (api)                                       │
│    - spring-boot-starter-data-jpa (api)                     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                       ogiri-core                             │
├─────────────────────────────────────────────────────────────┤
│  • OgiriToken (interface)                                   │
│  • OgiriTokenRepository (interface)                         │
│  • OgiriTokenService                                        │
│  • OgiriSecurityAutoConfiguration                           │
│  • OgiriFailureAnalyzer                                     │
│                                                              │
│  Dependencies:                                               │
│    - spring-boot-starter-security                           │
│    - spring-boot-starter-web                                │
│    - NO database dependencies                               │
└─────────────────────────────────────────────────────────────┘
```

### User Dependency Configuration

**For JPA users:**
```kotlin
dependencies {
    implementation("com.quantipixels.ogiri:ogiri-jpa:1.3.0")
    // Transitively includes ogiri-core
}
```

**For custom/other databases:**
```kotlin
dependencies {
    implementation("com.quantipixels.ogiri:ogiri-core:1.3.0")
    // Implement OgiriTokenRepository manually
}
```

---

## Improvements

### 1. Spring Configuration Processor (IDE Autocomplete)

**Location:** `ogiri-core`

**Changes:**
```kotlin
// ogiri-core/build.gradle.kts
annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
```

**Impact:** IDE autocomplete for all `ogiri.*` properties in IntelliJ/VS Code.

---

### 2. Configuration Metadata with Hints

**Location:** `ogiri-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`

```json
{
  "properties": [
    {
      "name": "ogiri.auth.max-clients",
      "type": "java.lang.Long",
      "description": "Maximum concurrent sessions per user",
      "defaultValue": 10
    },
    {
      "name": "ogiri.auth.rotate-stale-seconds",
      "type": "java.lang.Long",
      "description": "Force token rotation after this age (0=disabled)",
      "defaultValue": 0
    }
  ],
  "hints": [
    {
      "name": "ogiri.cookies.same-site",
      "values": [
        { "value": "Strict", "description": "Only same-site requests (most secure)" },
        { "value": "Lax", "description": "Same-site + top-level navigation" },
        { "value": "None", "description": "All requests (requires Secure=true)" }
      ]
    }
  ]
}
```

---

### 3. PasswordEncoder Auto-Configuration

**Location:** `ogiri-core`

**Changes to `OgiriSecurityAutoConfiguration.kt`:**

```kotlin
@Bean
@ConditionalOnMissingBean(PasswordEncoder::class)
fun ogiriPasswordEncoder(): PasswordEncoder {
    logger.info("No PasswordEncoder bean found, using BCryptPasswordEncoder")
    return BCryptPasswordEncoder()
}
```

**Benefit:** Users no longer need to define a PasswordEncoder bean manually.

---

### 4. Failure Analyzer for Missing Beans

**Location:** `ogiri-core`

**New File:** `ogiri-core/src/main/kotlin/.../config/OgiriFailureAnalyzer.kt`

```kotlin
class OgiriMissingBeanFailureAnalyzer : AbstractFailureAnalyzer<NoSuchBeanDefinitionException>() {

    override fun analyze(rootFailure: Throwable, cause: NoSuchBeanDefinitionException): FailureAnalysis? {
        val beanType = cause.beanType?.simpleName ?: return null

        return when {
            beanType.contains("OgiriTokenRepository") -> FailureAnalysis(
                "No OgiriTokenRepository bean found.",
                """
                Ogiri requires an OgiriTokenRepository<T> bean for token persistence.

                Option 1 - Use ogiri-jpa (recommended for JPA/Hibernate):
                  Add dependency: implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")
                  Then extend AbstractJpaTokenRepositoryAdapter

                Option 2 - Implement manually:
                  @Repository
                  class MyTokenRepository : OgiriTokenRepository<MyToken> { ... }

                Documentation: https://mosobande.github.io/ogiri/guides/database-integration/
                """.trimIndent(),
                cause
            )
            beanType.contains("OgiriUserDirectory") -> FailureAnalysis(
                "No OgiriUserDirectory bean found.",
                """
                Ogiri requires an OgiriUserDirectory bean to resolve users.

                Create:
                  @Component
                  class MyUserDirectory : OgiriUserDirectory {
                      override fun findById(id: Long): OgiriUser? = ...
                      override fun loadUserByUsername(username: String): OgiriUser = ...
                  }

                Documentation: https://mosobande.github.io/ogiri/quickstart/
                """.trimIndent(),
                cause
            )
            else -> null
        }
    }
}
```

**Register in:** `META-INF/spring/org.springframework.boot.diagnostics.FailureAnalyzer.imports`

---

### 5. Abstract JPA Repository Adapter

**Location:** `ogiri-jpa` (new module)

**File:** `ogiri-jpa/src/main/kotlin/.../jpa/AbstractJpaTokenRepositoryAdapter.kt`

```kotlin
/**
 * Base adapter that implements OgiriTokenRepository using Spring Data JPA.
 *
 * Eliminates ~80 lines of boilerplate. Users only need to:
 * 1. Create their JPA repository interface
 * 2. Extend this class and provide custom query delegations
 *
 * Example:
 * ```kotlin
 * @Repository
 * class MyTokenRepositoryAdapter(jpa: MyTokenJpaRepository) :
 *     AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(jpa) {
 *
 *     override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
 *         jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
 *
 *     override fun findByUserIdAndClientEquals(userId: Long, client: String) =
 *         jpaRepository.findByUserIdAndClient(userId, client)
 * }
 * ```
 */
abstract class AbstractJpaTokenRepositoryAdapter<T : OgiriToken, R : JpaRepository<T, Long>>(
    protected val jpaRepository: R
) : OgiriTokenRepository<T> {

    // Standard implementations provided
    override fun save(token: T): T = jpaRepository.save(token)
    override fun findById(id: Long): T? = jpaRepository.findById(id).orElse(null)
    override fun deleteById(id: Long) = jpaRepository.deleteById(id)
    override fun delete(token: T) = jpaRepository.delete(token)

    // Abstract - users must implement (custom queries)
    abstract fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>
    abstract fun findByUserIdAndClientEquals(userId: Long, client: String): T?
    abstract fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<T>
    abstract fun findByExpiryAtBeforeCutoff(cutoff: Instant): List<T>
    abstract fun deleteByUserIdAndClientEquals(userId: Long, client: String)
    abstract fun deleteByUserIdAndClientIdIn(userId: Long, clientIds: Collection<String>)
    abstract fun deleteByUserIdJpa(userId: Long)

    // Implementations that delegate to abstract methods
    override fun findAllByUserId(userId: Long): List<T> = findByUserIdOrderByUpdatedAtDesc(userId)
    override fun findByUserIdAndClient(userId: Long, clientId: String): T? =
        findByUserIdAndClientEquals(userId, clientId)
    // ... etc
}
```

**Benefit:** Reduces user code from ~80 lines to ~15 lines.

---

### 6. OgiriBaseTokenEntity (JPA MappedSuperclass)

**Location:** `ogiri-jpa` (new module)

**File:** `ogiri-jpa/src/main/kotlin/.../jpa/OgiriBaseTokenEntity.kt`

```kotlin
/**
 * Base JPA entity with all required token fields pre-configured.
 *
 * Users extend this class and add @Entity + @Table:
 * ```kotlin
 * @Entity
 * @Table(name = "tokens")
 * class MyToken : OgiriBaseTokenEntity()
 * ```
 *
 * All 15+ fields with proper JPA annotations are inherited:
 * - id (auto-generated)
 * - userId, client, token, tokenType
 * - expiryAt, tokenUpdatedAt, updatedAt, lastUsedAt
 * - previousToken, lastToken, tokenPrefix, tokenSubtype
 * - plainToken (transient, not persisted)
 */
@MappedSuperclass
abstract class OgiriBaseTokenEntity : OgiriBaseToken() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long = 0

    @Column(name = "user_id", nullable = false)
    override var userId: Long = 0

    @Column(nullable = false, length = 64)
    override var client: String = ""

    @Column(nullable = false, length = 512)
    override var token: String = ""

    @Column(name = "token_type", nullable = false, length = 16)
    override var tokenType: String = OgiriTokenType.APP.name

    @Column(name = "expiry_at", nullable = false)
    override var expiryAt: Instant = Instant.now()

    @Column(name = "token_updated_at", nullable = false)
    override var tokenUpdatedAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    override var updatedAt: Instant = Instant.now()

    @Column(name = "last_used_at")
    override var lastUsedAt: Instant? = null

    @Column(name = "previous_token", length = 512)
    override var previousToken: String? = null

    @Column(name = "last_token", length = 512)
    override var lastToken: String? = null

    @Column(name = "token_prefix", length = 16)
    override var tokenPrefix: String? = null

    @Column(name = "token_subtype", length = 32)
    override var tokenSubtype: String? = null

    @Transient
    override var plainToken: String? = null

    @PrePersist
    @PreUpdate
    fun updateTimestamp() {
        updatedAt = Instant.now()
    }
}
```

**Benefit:** Eliminates manual entity creation with 15+ fields and annotations.

---

## New Module: ogiri-jpa

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

group = "com.quantipixels.ogiri"

dependencies {
    api(project(":ogiri-core"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
}
```

### Auto-Configuration

```kotlin
@Configuration
@ConditionalOnClass(JpaRepository::class)
@AutoConfigureAfter(OgiriSecurityAutoConfiguration::class)
class OgiriJpaAutoConfiguration {
    // Any JPA-specific auto-configuration beans
}
```

### Registration

**File:** `ogiri-jpa/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.quantipixels.ogiri.jpa.OgiriJpaAutoConfiguration
```

---

## Roadmap: Future Database Adapters

| Module | Database | Priority | Status |
|--------|----------|----------|--------|
| `ogiri-jpa` | JPA/Hibernate | High | **Planned** |
| `ogiri-mongo` | MongoDB | Medium | Future |
| `ogiri-redis` | Redis | Medium | Future |
| `ogiri-jdbc` | Raw JDBC | Low | Future |
| `ogiri-r2dbc` | Reactive SQL | Low | Future |

### ogiri-mongo (Future)

```kotlin
// AbstractMongoTokenRepositoryAdapter
// OgiriBaseTokenDocument (@Document)
dependencies {
    api(project(":ogiri-core"))
    api("org.springframework.boot:spring-boot-starter-data-mongodb")
}
```

### ogiri-redis (Future)

```kotlin
// AbstractRedisTokenRepository
// OgiriTokenHash (@RedisHash)
dependencies {
    api(project(":ogiri-core"))
    api("org.springframework.boot:spring-boot-starter-data-redis")
}
```

---

## Implementation Order

| Phase | Item | Module | Effort |
|-------|------|--------|--------|
| 1 | Configuration Processor | ogiri-core | Low |
| 1 | Configuration Metadata | ogiri-core | Low |
| 1 | PasswordEncoder auto-config | ogiri-core | Low |
| 1 | Failure Analyzer | ogiri-core | Medium |
| 2 | Create ogiri-jpa module structure | ogiri-jpa | Medium |
| 2 | AbstractJpaTokenRepositoryAdapter | ogiri-jpa | Medium |
| 2 | OgiriBaseTokenEntity | ogiri-jpa | Medium |
| 2 | Update sample apps to use ogiri-jpa | samples | Low |
| 3 | Update documentation | docs | Medium |

---

## Files to Create/Modify

### Phase 1: ogiri-core improvements

**New Files:**
- `ogiri-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- `ogiri-core/src/main/kotlin/.../config/OgiriFailureAnalyzer.kt`
- `ogiri-core/src/main/resources/META-INF/spring/org.springframework.boot.diagnostics.FailureAnalyzer.imports`

**Modified Files:**
- `ogiri-core/build.gradle.kts` (add configuration processor)
- `ogiri-core/.../config/OgiriSecurityAutoConfiguration.kt` (add PasswordEncoder bean)

### Phase 2: ogiri-jpa module

**New Files:**
- `ogiri-jpa/build.gradle.kts`
- `ogiri-jpa/src/main/kotlin/com/quantipixels/ogiri/jpa/OgiriJpaAutoConfiguration.kt`
- `ogiri-jpa/src/main/kotlin/com/quantipixels/ogiri/jpa/AbstractJpaTokenRepositoryAdapter.kt`
- `ogiri-jpa/src/main/kotlin/com/quantipixels/ogiri/jpa/OgiriBaseTokenEntity.kt`
- `ogiri-jpa/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Modified Files:**
- `settings.gradle.kts` (include ogiri-jpa)
- `sample/sample-kotlin/build.gradle.kts` (use ogiri-jpa)
- `sample/sample-java/build.gradle.kts` (use ogiri-jpa)

---

## Success Criteria

After implementation:

1. **IDE autocomplete works** for all `ogiri.*` properties
2. **Missing bean errors** show actionable guidance instead of stack traces
3. **JPA users** can integrate with ~20 lines instead of ~100 lines:
   ```kotlin
   // Entity: 2 lines
   @Entity @Table(name = "tokens")
   class Token : OgiriBaseTokenEntity()

   // Repository: 2 lines
   @Repository
   interface TokenJpaRepo : JpaRepository<Token, Long> { ... }

   // Adapter: ~15 lines (vs ~80 before)
   @Repository
   class TokenRepoAdapter(jpa: TokenJpaRepo) :
       AbstractJpaTokenRepositoryAdapter<Token, TokenJpaRepo>(jpa) { ... }
   ```
4. **PasswordEncoder** is auto-configured (one less manual bean)
5. **Roadmap documented** for future MongoDB/Redis adapters
