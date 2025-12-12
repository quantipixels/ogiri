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
