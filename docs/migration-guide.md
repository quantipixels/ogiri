# Migration Guide

## Migrating to 1.3.0

Version 1.3.0 introduces the new `ogiri-jpa` module for simplified JPA integration. This is a fully backward-compatible release—existing code continues to work without changes.

### What's New

1. **`ogiri-jpa` Module**: New optional module with JPA helpers
2. **PasswordEncoder Auto-Configuration**: BCrypt provided automatically if none exists
3. **Failure Analyzer**: Helpful error messages for missing beans

### Option 1: No Changes Required

If you're happy with your current setup, simply update the version:

```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.3.0")
```

### Option 2: Migrate to ogiri-jpa (Recommended for JPA Users)

The new `ogiri-jpa` module reduces JPA boilerplate by ~70%.

#### Step 1: Update Dependency

```kotlin
// Before
implementation("com.quantipixels.ogiri:ogiri-core:1.2.x")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")

// After
implementation("com.quantipixels.ogiri:ogiri-jpa:1.3.0")
// ogiri-jpa includes ogiri-core and spring-boot-starter-data-jpa transitively
```

#### Step 2: Simplify Token Entity

**Before (~250 lines):**
```kotlin
@Entity
@Table(name = "tokens", ...)
data class MyToken(
    @Id @GeneratedValue
    override val id: Long = 0,
    @Column(name = "user_id")
    override val userId: Long = 0,
    @Column(name = "client")
    override val client: String = "",
    // ... 15+ more fields with annotations
) : OgiriBaseToken()
```

**After (~10 lines):**
```kotlin
@Entity
@Table(
    name = "tokens",
    indexes = [Index(name = "idx_tokens_user_id", columnList = "user_id")],
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "client"])]
)
class MyToken : OgiriBaseTokenEntity()
```

#### Step 3: Simplify Repository

**Before (~90 lines):**
```kotlin
@Repository
class MyTokenRepositoryAdapter(
    private val jpaRepository: MyTokenJpaRepository
) : OgiriTokenRepository<MyToken> {
    override fun save(token: MyToken) = jpaRepository.save(token)
    override fun findById(id: Long) = jpaRepository.findById(id).orElse(null)
    // ... 10+ more method implementations
}
```

**After (~30 lines):**
```kotlin
@Repository
@Primary
class MyTokenRepositoryAdapter(
    jpaRepository: MyTokenJpaRepository
) : AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(jpaRepository) {
    override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
        jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)
    // Only implement 7 abstract methods that delegate to JPA
}
```

#### Step 4: Remove PasswordEncoder Bean (Optional)

If your only `PasswordEncoder` bean was for Ogiri, you can remove it:

```kotlin
// Can be deleted - Ogiri auto-configures BCryptPasswordEncoder
@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

### Verification

After migration:

1. Run `./gradlew build` to verify compilation
2. Run `./gradlew test` to ensure tests pass
3. Your token table schema remains unchanged

---

## Migrating to 1.2.0

Version 1.2.0 introduces an **Interface-First Design** to Ogiri Security. This change decouples the core logic from specific base classes, allowing for greater flexibility in how you implement your tokens.

While this is a significant architectural improvement, the migration path is straightforward and primarily involves updating class names and imports.

### Summary of Changes

1.  **New Interface**: `OgiriToken` is now the primary contract.
2.  **Renamed Classes**: Core classes have been prefixed with `Ogiri` for consistency and to avoid conflicts.
3.  **Updated Generics**: Type bounds now use `<T : OgiriToken>` instead of `<T : BaseToken>`.

### Renamed Classes

The following classes have been renamed. You will need to update your imports and references.

| Old Name             | New Name                  | Purpose                             |
| -------------------- | ------------------------- | ----------------------------------- |
| `BaseToken`          | `OgiriBaseToken`          | Convenience base class for entities |
| `TokenRepository<T>` | `OgiriTokenRepository<T>` | Persistence interface               |
| `TokenService<T>`    | `OgiriTokenService<T>`    | Main service class                  |
| `GeneratedTokens<T>` | `OgiriGeneratedTokens<T>` | Return type for token generation    |

### Other Renamed Classes

The following supporting classes were also renamed for consistency:

| Old Name               | New Name                    |
| :--------------------- | :-------------------------- |
| `RouteRegistry`        | `OgiriRouteRegistry`        |
| `SubTokenRegistration` | `OgiriSubTokenRegistration` |
| `SubTokenRegistry`     | `OgiriSubTokenRegistry`     |

### Migration Steps

#### 1. Update Imports

Perform a global find-and-replace in your project to update the class names.

**Find**: `com.quantipixels.ogiri.security.tokens.BaseToken`
**Replace**: `com.quantipixels.ogiri.security.tokens.OgiriBaseToken`

**Find**: `com.quantipixels.ogiri.security.tokens.TokenRepository`
**Replace**: `com.quantipixels.ogiri.security.tokens.OgiriTokenRepository`

**Find**: `com.quantipixels.ogiri.security.tokens.TokenService`
**Replace**: `com.quantipixels.ogiri.security.tokens.OgiriTokenService`

#### 2. Update Token Entity

If you were extending `BaseToken`, change it to extend `OgiriBaseToken`.

**Before:**
```kotlin
@Entity
data class MyToken(
    // ...
) : BaseToken()
```

**After:**
```kotlin
@Entity
data class MyToken(
    // ...
) : OgiriBaseToken()
```

Alternatively, you can now implement the `OgiriToken` interface directly if you prefer not to use the base class.

#### 3. Update Repository

Update your repository interface to extend `OgiriTokenRepository`.

**Before:**
```kotlin
interface MyTokenRepository : TokenRepository<MyToken>
```

**After:**
```kotlin
interface MyTokenRepository : OgiriTokenRepository<MyToken>
```

#### 4. Update Service Injection

Update where you inject the token service.

**Before:**
```kotlin
class MyController(
    private val tokenService: TokenService<MyToken>
)
```

**After:**
```kotlin
class MyController(
    private val tokenService: OgiriTokenService<MyToken>
)
```

### Type Bounds Update

If you have any custom extensions or functions that were generic over `<T : BaseToken>`, update them to `<T : OgiriToken>`.

```kotlin
// Old
fun <T : BaseToken> doSomething(token: T) { ... }

// New
fun <T : OgiriToken> doSomething(token: T) { ... }
```

### Verification

After making these changes:
1.  Run your compilation build to ensure all references are updated.
2.  Run your test suite. The logic remains backward compatible, so tests should pass without functional changes.
