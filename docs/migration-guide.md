# Migration Guide

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
