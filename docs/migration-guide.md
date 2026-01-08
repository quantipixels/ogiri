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

### Method Renames (Breaking Changes)

#### 1. OgiriRouteRegistry: `registrations()` → `routes()`

The `OgiriRouteRegistry` interface (formerly `RouteRegistry`) has renamed its primary method to better reflect its purpose.

**Before:**

=== "Kotlin"

    ```kotlin
    @Component
    class MyRouteRegistry : RouteRegistry {
      override fun registrations() = listOf(
        OgiriRoute.post("/api/auth/login")
      )
    }
    ```

=== "Java"

    ```java
    @Component
    public class MyRouteRegistry implements RouteRegistry {
      @Override
      public List<OgiriRoute> registrations() {
        return List.of(
          OgiriRoute.post("/api/auth/login")
        );
      }
    }
    ```

**After:**

=== "Kotlin"

    ```kotlin
    @Component
    class MyRouteRegistry : OgiriRouteRegistry {
      override fun routes() = listOf(
        OgiriRoute.post("/api/auth/login"),
        OgiriRoute.post("/api/auth/register"),
        OgiriRoute.get("/api/health"),
      )
    }
    ```

=== "Java"

    ```java
    @Component
    public class MyRouteRegistry implements OgiriRouteRegistry {
      @Override
      public List<OgiriRoute> routes() {
        return List.of(
          OgiriRoute.post("/api/auth/login"),
          OgiriRoute.post("/api/auth/register"),
          OgiriRoute.get("/api/health")
        );
      }
    }
    ```

#### 2. OgiriUser: `userId` property → `getOgiriUserId()` method

The `OgiriUser` interface has changed from a Kotlin property to an explicit method to improve Java compatibility and align with the interface-first design.

**Before:**

=== "Kotlin"

    ```kotlin
    class MyUser(override val userId: Long) : OgiriUser {
        // ...
    }
    ```

=== "Java"

    ```java
    public class MyUser implements OgiriUser {
        @Override
        public Long getUserId() {
            return 1L;
        }
    }
    ```

**After:**

=== "Kotlin"

    ```kotlin
    class MyUser : OgiriUser {
        override fun getOgiriUserId(): Long = 1L
        // ...
    }
    ```

=== "Java"

    ```java
    public class MyUser implements OgiriUser {
        @Override
        public Long getOgiriUserId() {
            return 1L;
        }
    }
    ```

!!! note "Compatibility Note"
This is a breaking change. All implementations of `OgiriUser` must be updated to implement `getOgiriUserId()`. Usages of the `userId` property in Kotlin or `getUserId()` in Java must be replaced with `getOgiriUserId()`. Ensure tests and any third-party integrations are updated to reflect this signature change.

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

1. Run your build to ensure all references are updated.
2. Run your test suite. The logic remains backward compatible, so tests should pass without functional changes.

---

## Migrating to 1.3.0 (Performance Optimizations)

Version 1.3.0 introduces significant performance optimizations, including token prefix indexing for O(1) lookups. This requires a database schema change.

### Database Schema Migration

Add the `token_prefix` column to your tokens table:

```sql
-- PostgreSQL
ALTER TABLE user_tokens ADD COLUMN token_prefix VARCHAR(8);
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix)
  WHERE token_type = 'app' AND expiry_at > NOW();

-- MySQL
ALTER TABLE user_tokens ADD COLUMN token_prefix VARCHAR(8);
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix);

-- H2 (for testing)
ALTER TABLE user_tokens ADD COLUMN token_prefix VARCHAR(8);
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix);
```

### Backwards Compatibility

The migration is **non-breaking**:

1. **Existing tokens**: Will have `NULL` for `token_prefix`. The token service falls back to scanning all tokens when prefix is `NULL`, maintaining full backwards compatibility.

2. **New tokens**: Will automatically have `token_prefix` populated with the first 8 characters of the plaintext token.

3. **Gradual migration**: As users authenticate and tokens rotate, new tokens will have the prefix populated. Over time (typically within your `token-lifespan-days` period), all active tokens will have prefixes.

### New Configuration Options

```yaml
ogiri:
  auth:
    max-bearer-token-size: 8192 # New: Max bearer token size (DoS protection)
  cleanup:
    batch-size: 1000 # New: Tokens deleted per batch
```

### Startup Warnings

The library now logs warnings for insecure configurations at startup:

- `ogiri.auth.rotate-stale-seconds=0` - Consider setting to 3600+ for production
- `ogiri.cookies.secure=false` - Enable for HTTPS deployments
- `ogiri.cookies.http-only=false` - Enable to prevent XSS cookie theft

### OgiriToken Interface Changes

If you implement `OgiriToken` directly (not extending `OgiriBaseToken`), add the new property:

```kotlin
interface OgiriToken {
    // ... existing properties ...
    var tokenPrefix: String?  // New: First 8 chars for O(1) lookup
}
```

`OgiriBaseToken` already includes this property with a default value of `null`, so implementations extending the base class require no changes.

### Repository Interface Changes

`OgiriTokenRepository` has new methods with default implementations:

| Method                                  | Purpose                                        |
| --------------------------------------- | ---------------------------------------------- |
| `findValidTokensByPrefix(prefix, now)`  | O(1) token lookup by prefix                    |
| `countByUserId(userId)`                 | Efficient token count for cleanup optimization |
| `deleteExpiredBatch(cutoff, batchSize)` | Batched cleanup for large datasets             |

These have default implementations using existing methods, so no changes are required unless you want to override them with optimized database queries.

### Verification

1. Run the database migration
2. Rebuild and restart your application
3. Verify tokens work as expected (existing tokens continue working)
4. Monitor logs for startup warnings and address any insecure configurations
