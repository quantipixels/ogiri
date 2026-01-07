# Plan: Bean Tooling Improvements for Ogiri

## Problem Statement

Currently, integrating Ogiri requires significant boilerplate:
1. ~80 lines of adapter code for `OgiriTokenRepository` (JPA)
2. Manual token entity with 15+ fields
3. No IDE autocomplete for `ogiri.*` properties
4. No helpful error messages when required beans are missing
5. Users must manually configure `PasswordEncoder`

## Proposed Improvements

### 1. Spring Configuration Processor (IDE Autocomplete)

**Goal:** Enable IntelliJ/VS Code autocomplete for `ogiri.*` properties.

**Changes:**
- Add `annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")` to `build.gradle.kts`
- Create `META-INF/additional-spring-configuration-metadata.json` with:
  - Property descriptions and default values
  - Enum hints for `cookies.same-site` (Strict, Lax, None)
  - Deprecation markers for future property changes

**Impact:** Low risk, high developer experience improvement.

---

### 2. Configuration Metadata with Hints

**Goal:** Provide rich IDE hints beyond basic autocomplete.

**File:** `ogiri-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`

```json
{
  "hints": [
    {
      "name": "ogiri.cookies.same-site",
      "values": [
        { "value": "Strict", "description": "Only sent with same-site requests (most secure)" },
        { "value": "Lax", "description": "Sent with same-site and top-level navigation" },
        { "value": "None", "description": "Sent with all requests (requires Secure=true)" }
      ]
    }
  ]
}
```

**Impact:** Low risk, improves discoverability.

---

### 3. Abstract JPA Repository Adapter

**Goal:** Reduce ~80 lines of adapter boilerplate to ~5 lines.

**New File:** `ogiri-core/src/main/kotlin/.../jpa/AbstractJpaTokenRepositoryAdapter.kt`

```kotlin
abstract class AbstractJpaTokenRepositoryAdapter<T : OgiriToken, R : JpaRepository<T, Long>>(
    protected val jpaRepository: R
) : OgiriTokenRepository<T> {

    override fun save(token: T): T = jpaRepository.save(token)
    override fun findById(id: Long): T? = jpaRepository.findById(id).orElse(null)
    override fun deleteById(id: Long) = jpaRepository.deleteById(id)
    // ... all standard implementations

    // Abstract methods users must implement (custom queries):
    abstract fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>
    abstract fun findByUserIdAndClient(userId: Long, client: String): T?
}
```

**User code becomes:**
```kotlin
@Repository
class MyTokenRepositoryAdapter(repo: MyTokenJpaRepository) :
    AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(repo) {

    override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
        jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
    // ... only custom query delegations
}
```

**Trade-off:** Adds optional JPA dependency. Should be in separate module or use `@ConditionalOnClass`.

**Alternative:** Keep in core with `compileOnly("org.springframework.data:spring-data-jpa")` so it compiles but doesn't force runtime dependency.

---

### 4. OgiriBaseToken Abstract Entity (Optional JPA Module)

**Goal:** Provide a ready-to-extend JPA entity with all required fields.

**Option A: In ogiri-core (compileOnly JPA)**

```kotlin
@MappedSuperclass
abstract class OgiriBaseTokenEntity : OgiriBaseToken() {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override var id: Long = 0

    @Column(nullable = false)
    override var userId: Long = 0

    @Column(nullable = false, length = 64)
    override var client: String = ""

    // ... all 15+ fields with proper annotations
}
```

**User code becomes:**
```kotlin
@Entity
@Table(name = "tokens")
class Token : OgiriBaseTokenEntity()
```

**Option B: Separate ogiri-jpa module**
- New module: `ogiri-jpa` with dependency on `ogiri-core`
- Contains JPA entity, adapter, and auto-configuration
- Users add `implementation("com.quantipixels.ogiri:ogiri-jpa:VERSION")`

**Trade-off:** Option A is simpler but adds compile-time JPA dependency to core. Option B is cleaner but adds module complexity.

---

### 5. PasswordEncoder Auto-Configuration

**Goal:** Auto-configure BCryptPasswordEncoder if none provided.

**Changes to `OgiriSecurityAutoConfiguration.kt`:**

```kotlin
@Bean
@ConditionalOnMissingBean(PasswordEncoder::class)
fun ogiriPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
```

**Impact:** Reduces required user configuration. Very low risk since it's conditional.

---

### 6. Failure Analyzer for Missing Beans

**Goal:** Provide helpful error messages instead of cryptic Spring injection failures.

**New File:** `ogiri-core/src/main/kotlin/.../config/OgiriFailureAnalyzer.kt`

```kotlin
class OgiriMissingBeanFailureAnalyzer : AbstractFailureAnalyzer<NoSuchBeanDefinitionException>() {

    override fun analyze(rootFailure: Throwable, cause: NoSuchBeanDefinitionException): FailureAnalysis? {
        val beanType = cause.beanType?.simpleName ?: return null

        return when (beanType) {
            "OgiriTokenRepository" -> FailureAnalysis(
                "No OgiriTokenRepository bean found.",
                """
                Ogiri requires an OgiriTokenRepository<T> bean for token persistence.

                For JPA, create:
                  @Repository
                  class MyTokenRepository(jpa: JpaRepository<MyToken, Long>) : OgiriTokenRepository<MyToken> { ... }

                See: https://mosobande.github.io/ogiri/guides/database-integration/
                """.trimIndent(),
                cause
            )
            "OgiriUserDirectory" -> FailureAnalysis(
                "No OgiriUserDirectory bean found.",
                """
                Ogiri requires an OgiriUserDirectory bean to resolve users.

                Create:
                  @Component
                  class MyUserDirectory : OgiriUserDirectory { ... }

                See: https://mosobande.github.io/ogiri/quickstart/
                """.trimIndent(),
                cause
            )
            else -> null
        }
    }
}
```

**Register in:** `META-INF/spring.factories` or `META-INF/spring/org.springframework.boot.diagnostics.FailureAnalyzer.imports`

**Impact:** Dramatically improves onboarding experience.

---

## Implementation Order (Recommended)

| Priority | Item | Effort | Risk | Impact |
|----------|------|--------|------|--------|
| 1 | Configuration Processor + Metadata | Low | Low | High |
| 2 | Failure Analyzer | Medium | Low | High |
| 3 | PasswordEncoder auto-config | Low | Low | Medium |
| 4 | Abstract JPA Adapter | Medium | Medium | High |
| 5 | Base Token Entity | Medium | Medium | High |

---

## Questions for Review

1. **JPA Support Location:** Should JPA helpers be:
   - (A) In `ogiri-core` with `compileOnly` dependency?
   - (B) In separate `ogiri-jpa` module?
   - (C) Not provided (keep current approach)?

2. **MongoDB/Redis:** Should similar adapters be provided for:
   - MongoDB (`ogiri-mongo`)?
   - Redis (`ogiri-redis`)?

3. **Base Token Entity:** Should we provide:
   - (A) Abstract `@MappedSuperclass` for JPA?
   - (B) Just better documentation?
   - (C) Code generator / template?

4. **Scope:** Implement all 6 items, or subset?

---

## Files to Create/Modify

### New Files
- `ogiri-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- `ogiri-core/src/main/kotlin/.../config/OgiriFailureAnalyzer.kt`
- `ogiri-core/src/main/kotlin/.../jpa/AbstractJpaTokenRepositoryAdapter.kt` (if approved)
- `ogiri-core/src/main/kotlin/.../jpa/OgiriBaseTokenEntity.kt` (if approved)

### Modified Files
- `ogiri-core/build.gradle.kts` (add configuration processor)
- `ogiri-core/src/main/kotlin/.../config/OgiriSecurityAutoConfiguration.kt` (PasswordEncoder bean)
- `ogiri-core/src/main/resources/META-INF/spring/...` (failure analyzer registration)
