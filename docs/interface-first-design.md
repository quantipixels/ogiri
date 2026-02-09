# Interface-First Design

The Ogiri security library uses an **interface-first design pattern** to provide maximum flexibility while maintaining a clean, predictable API.

## Core Principle

> "Program to interfaces, not implementations"

This means:

1. **Define what the library needs** via interfaces (`OgiriToken`, `OgiriTokenRepository`, `OgiriTokenService`)
2. **Don't prescribe how users implement it** - no forced inheritance
3. **Provide convenient base implementations** for simple cases (`OgiriBaseToken`)
4. **Let power users compose their own solutions** without restrictions

## Core Interfaces

### OgiriToken

The primary contract for all token implementations.

```kotlin
interface OgiriToken {
    val id: Long
    val userId: Long
    val client: String
    var token: String
    val tokenType: String
    var expiryAt: Instant
    val createdAt: Instant
    val updatedAt: Instant
    var tokenUpdatedAt: Instant
    var tokenSubtype: String?
    var lastToken: String?
    var previousToken: String?
    var lastUsedAt: Instant?
    var plainToken: String?

    fun isExpired(now: Instant = Instant.now()): Boolean = expiryAt.isBefore(now)
}
```

**No implementation required** - you just declare which interface you implement.

### OgiriTokenRepository\<T : OgiriToken\>

Database-agnostic persistence contract.

```kotlin
interface OgiriTokenRepository<T : OgiriToken> {
    fun save(token: T): T
    fun findById(id: Long): Optional<T>
    fun deleteById(id: Long)
    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<T>
    fun findByUserIdAndClient(userId: Long, client: String): Optional<T>
    // ... and more
}
```

Implement with:

- Spring Data JPA
- JDBC Template
- MongoDB
- Redis
- Any persistence layer you prefer

### OgiriTokenService\<T : OgiriToken\>

Core token orchestration service.

```kotlin
open class OgiriTokenService<T : OgiriToken>(
    private val repository: OgiriTokenRepository<T>,
    private val passwordEncoder: PasswordEncoder,
    private val userDirectory: OgiriUserDirectory,
    private val identifierPolicy: IdentifierPolicy,
    private val subTokenRegistry: OgiriSubTokenRegistry,
    protected val properties: OgiriConfigurationProperties,
    auditHookProvider: ObjectProvider<OgiriAuditHook>,
    rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
)
```

Works with any `OgiriToken` implementation. No need to extend - just provide a token that implements the interface.

## Implementation Patterns

### Pattern 1: Direct Interface Implementation (Maximum Flexibility)

For complete control over your token structure:

```kotlin
@Entity
@Table(name = "user_tokens")
data class MyToken(
    @Id @GeneratedValue override val id: Long = 0,
    @Column(name = "user_id") override val userId: Long,
    @Column(name = "client_id") override val client: String,
    @Column(name = "token_hash") override var token: String,
    @Column(name = "token_type") override val tokenType: String,
    @Column(name = "expiry_at") override var expiryAt: Instant,
    @CreationTimestamp override val createdAt: Instant = Instant.now(),
    @UpdateTimestamp override val updatedAt: Instant = Instant.now(),
    @Column(name = "token_updated_at") override var tokenUpdatedAt: Instant,
    @Column(name = "token_subtype") override var tokenSubtype: String? = null,
    @Column(name = "last_token_hash") override var lastToken: String? = null,
    @Column(name = "previous_token_hash") override var previousToken: String? = null,
    @Column(name = "last_used_at") override var lastUsedAt: Instant? = null,

    // Your custom fields
    @Column(name = "device_id") val deviceId: String? = null,
    @Column(name = "ip_address") val ipAddress: String? = null,
) : OgiriToken {
    @Transient
    override var plainToken: String? = null
}
```

**Advantages:**

- Complete flexibility over token structure
- Can add custom fields freely
- Can inherit from your own base class (via mixin if needed in Kotlin, multiple inheritance patterns in Java)
- Works exactly like before

### Pattern 2: Extend OgiriBaseToken (Convenience)

For straightforward implementations:

```kotlin
@Entity
@Table(name = "user_tokens")
data class MyToken(
    @Id @GeneratedValue override val id: Long = 0,
    @Column(name = "user_id") override val userId: Long,
    @Column(name = "client_id") override val client: String,
    @Column(name = "token_hash") override var token: String,
    @Column(name = "token_type") override val tokenType: String,
    @Column(name = "expiry_at") override var expiryAt: Instant,
    @CreationTimestamp override val createdAt: Instant = Instant.now(),
    @UpdateTimestamp override val updatedAt: Instant = Instant.now(),
    @Column(name = "token_updated_at") override var tokenUpdatedAt: Instant,
) : OgiriBaseToken()
```

**Advantages:**

- Sensible defaults for optional properties
- Less boilerplate code
- Still provides all required properties
- Clear inheritance hierarchy

## Repository Implementation

### Using Spring Data JPA

```kotlin
@Repository
interface MyTokenRepository : JpaRepository<MyToken, Long>, OgiriTokenRepository<MyToken> {
    override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<MyToken> =
        findByUserIdOrderByUpdatedAtDescOrderByUpdatedAtDesc(userId)

    fun findByUserIdOrderByUpdatedAtDescOrderByUpdatedAtDesc(userId: Long): List<MyToken>

    @Query("SELECT t FROM MyToken t WHERE t.userId = :userId AND t.client = :client")
    override fun findByUserIdAndClient(
        @Param("userId") userId: Long,
        @Param("client") clientId: String
    ): MyToken?

    // ... implement other OgiriTokenRepository methods
}
```

### Using Plain JDBC

```kotlin
@Repository
class MyTokenRepository(private val jdbcTemplate: JdbcTemplate) : OgiriTokenRepository<MyToken> {
    override fun save(token: MyToken): MyToken {
        // INSERT or UPDATE logic
        return token
    }

    override fun findById(id: Long): MyToken? {
        // SELECT logic
        return null
    }

    // ... implement other methods
}
```

## Service Implementation

### Custom Token Factory

Extend `OgiriTokenService` and provide a custom token factory:

```kotlin
@Service
class MyTokenService(
    repository: OgiriTokenRepository<MyToken>,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
    auditHookProvider: ObjectProvider<OgiriAuditHook>,
    rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
) : OgiriTokenService<MyToken>(
    repository, passwordEncoder, userDirectory,
    identifierPolicy, subTokenRegistry, properties,
    auditHookProvider, rateLimitHookProvider,
) {
    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: OgiriTokenType,
        expiry: Instant,
        tokenSubtype: String?,
        plainTokenValue: String,
    ): MyToken = MyToken(
        userId = userId,
        client = client,
        token = hashedToken,
        tokenType = tokenType.label,
        expiryAt = expiry,
        tokenSubtype = tokenSubtype,
        plainToken = plainTokenValue
    )
}
```

## Design Benefits

### 1. Flexibility

- No inheritance constraints
- Your token can extend your own base class
- Add custom fields without modifying library code
- Use any persistence layer

### 2. Testability

- Implement `OgiriToken` with a simple data class for tests
- No need for database setup in unit tests
- Mock `OgiriTokenRepository` easily

### 3. Composition

- Mix and match implementations
- Use different token types for different purposes
- Easy to extend without modifying existing code

### 4. Clarity

- Interfaces document the contract explicitly
- No implicit dependencies on base classes
- Easy to understand what's required

## Complete Example

### Token Entity (Direct Implementation)

```kotlin
@Entity
@Table(
    name = "user_tokens",
    indexes = [
        Index(name = "idx_tokens_user_id", columnList = "user_id"),
        Index(name = "idx_tokens_expiry", columnList = "expiry_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tokens_user_client", columnNames = ["user_id", "client_id"])
    ]
)
data class UserToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    override val userId: Long,

    @Column(name = "client_id", nullable = false)
    override val client: String,

    @Column(name = "token_hash", nullable = false)
    override var token: String,

    @Column(name = "token_type", nullable = false)
    override val tokenType: String = "app",

    @Column(name = "expiry_at", nullable = false)
    override var expiryAt: Instant,

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    override val createdAt: Instant = Instant.now(),

    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    override val updatedAt: Instant = Instant.now(),

    @Column(name = "token_updated_at", nullable = false)
    override var tokenUpdatedAt: Instant = Instant.now(),

    @Column(name = "token_subtype")
    override var tokenSubtype: String? = null,

    @Column(name = "last_token_hash")
    override var lastToken: String? = null,

    @Column(name = "previous_token_hash")
    override var previousToken: String? = null,

    @Column(name = "last_used_at")
    override var lastUsedAt: Instant? = null,
) : OgiriToken {
    @Transient
    override var plainToken: String? = null
}
```

### Repository

```kotlin
@Repository
interface UserTokenRepository : JpaRepository<UserToken, Long>, OgiriTokenRepository<UserToken> {
    // ... implement required methods
}
```

### Service

```kotlin
@Service
class UserTokenService(
    repository: OgiriTokenRepository<UserToken>,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: OgiriSubTokenRegistry,
    properties: OgiriConfigurationProperties,
    auditHookProvider: ObjectProvider<OgiriAuditHook>,
    rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
) : OgiriTokenService<UserToken>(
    repository, passwordEncoder, userDirectory,
    identifierPolicy, subTokenRegistry, properties,
    auditHookProvider, rateLimitHookProvider,
) {
    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: OgiriTokenType,
        expiry: Instant,
        tokenSubtype: String?,
        plainTokenValue: String,
    ): UserToken = UserToken(
        userId = userId,
        client = client,
        token = hashedToken,
        tokenType = tokenType.label,
        expiryAt = expiry,
        tokenSubtype = tokenSubtype,
        plainToken = plainTokenValue
    )
}
```

## Summary

The interface-first design gives you:

✅ **Maximum flexibility** - Implement the interfaces your way
✅ **No forced inheritance** - Use composition if you prefer
✅ **Clear contracts** - Interfaces document exactly what's needed
✅ **Easy testing** - Mock implementations are straightforward
✅ **Backward compatible** - Existing `OgiriBaseToken` still works

For simple cases, extend `OgiriBaseToken`. For complex needs, implement `OgiriToken` directly. Both approaches work perfectly with the rest of the library.
