# Implementation and Integration Guide

This guide walks you through implementing and integrating Ogiri token authentication into your Spring Boot application.

## Table of Contents

1. [Token Entity Implementation](#token-entity-implementation)
2. [Repository Implementation](#repository-implementation)
3. [Service Configuration](#service-configuration)
4. [User Directory Implementation](#user-directory-implementation)
5. [Integration Steps](#integration-steps)
6. [Testing](#testing)

## Token Entity Implementation

### Core Contract: OgiriToken

Every token in Ogiri must implement the `OgiriToken` interface. This defines all properties the library needs to manage tokens.

### Option 1: Extend OgiriBaseToken (Recommended for Simple Cases)

```kotlin
import com.quantipixels.ogiri.security.tokens.OgiriBaseToken
import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(
    name = "tokens",
    indexes = [
        Index(name = "idx_tokens_user", columnList = "user_id"),
        Index(name = "idx_tokens_expiry", columnList = "expiry_at")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_tokens_user_client", columnNames = ["user_id", "client_id"])
    ]
)
data class Token(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    override val userId: Long = 0,

    @Column(name = "client_id", nullable = false)
    override val client: String = "",

    @Column(name = "token_hash", nullable = false)
    override var token: String = "",

    @Column(name = "token_type", nullable = false)
    override val tokenType: String = "APP",

    @Column(name = "expiry_at", nullable = false)
    override var expiryAt: Instant = Instant.now(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    override val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    override val updatedAt: Instant = Instant.now(),

    @Column(name = "token_updated_at", nullable = false)
    override var tokenUpdatedAt: Instant = Instant.now(),
) : OgiriBaseToken()
```

**Key Points:**

- All abstract properties from `OgiriBaseToken` must be implemented
- Optional properties (`tokenSubtype`, `lastToken`, etc.) have defaults
- `plainToken` is automatically managed as transient
- JPA annotations handle persistence details

### Option 2: Direct Interface Implementation (Maximum Flexibility)

```kotlin
import com.quantipixels.ogiri.security.tokens.OgiriToken
import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "custom_tokens")
data class CustomToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    override val userId: Long = 0,

    @Column(name = "client_id", nullable = false)
    override val client: String = "",

    @Column(name = "token_hash", nullable = false)
    override var token: String = "",

    @Column(name = "token_type", nullable = false)
    override val tokenType: String = "APP",

    @Column(name = "expiry_at", nullable = false)
    override var expiryAt: Instant = Instant.now(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    override val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
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

    // Your custom fields
    @Column(name = "device_id")
    val deviceId: String? = null,

    @Column(name = "user_agent")
    val userAgent: String? = null,
) : OgiriToken {
    @Transient
    override var plainToken: String? = null
}
```

**Advantages:**

- Add custom columns freely (`deviceId`, `userAgent`)
- Full control over the table structure
- Can implement your own inheritance patterns

### Database Schema Requirements

Ogiri requires these columns for any token table:

```sql
CREATE TABLE tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    token_type VARCHAR(10) NOT NULL,
    expiry_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    token_updated_at TIMESTAMP NOT NULL,
    token_subtype VARCHAR(50),
    last_token_hash VARCHAR(255),
    previous_token_hash VARCHAR(255),
    last_used_at TIMESTAMP,
    UNIQUE KEY uk_tokens_user_client (user_id, client_id),
    INDEX idx_tokens_user (user_id),
    INDEX idx_tokens_expiry (expiry_at)
);
```

## Repository Implementation

The `OgiriTokenRepository<T>` interface defines all persistence operations needed.

### Using Spring Data JPA

```kotlin
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface TokenRepository : JpaRepository<Token, Long>, OgiriTokenRepository<Token> {

    // Required by OgiriTokenRepository interface

    override fun findAllByUserId(userId: Long): List<Token> =
        findAllByUserIdOrderByUpdatedAtDesc(userId)

    @Query("SELECT t FROM Token t WHERE t.userId = :userId ORDER BY t.updatedAt DESC")
    fun findAllByUserIdOrderByUpdatedAtDesc(
        @Param("userId") userId: Long
    ): List<Token>

    @Query("SELECT t FROM Token t WHERE t.userId = :userId AND t.client = :client")
    override fun findByUserIdAndClient(
        @Param("userId") userId: Long,
        @Param("client") clientId: String
    ): Token?

    @Query("SELECT t FROM Token t WHERE t.userId = :userId AND t.tokenSubtype = :subtype ORDER BY t.updatedAt DESC")
    override fun findAllByUserIdAndTokenSubtype(
        @Param("userId") userId: Long,
        @Param("subtype") tokenSubtype: String
    ): List<Token>

    @Query("SELECT t FROM Token t WHERE t.expiryAt < :cutoff")
    override fun findByExpiryAtBefore(
        @Param("cutoff") cutoff: Instant
    ): List<Token>

    override fun deleteByUserIdAndClient(userId: Long, clientId: String) {
        val token = findByUserIdAndClient(userId, clientId)
        token?.let { delete(it) }
    }

    override fun deleteByUserIdAndClientIn(userId: Long, clientIds: Collection<String>) {
        clientIds.forEach { clientId ->
            deleteByUserIdAndClient(userId, clientId)
        }
    }

    override fun deleteByUserId(userId: Long) {
        findAllByUserId(userId).forEach { delete(it) }
    }
}
```

### Using JDBC Template

```kotlin
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class TokenJdbcRepository(private val jdbcTemplate: JdbcTemplate) : OgiriTokenRepository<Token> {

    override fun save(token: Token): Token {
        if (token.id == 0L) {
            // INSERT
            val sql = """
                INSERT INTO tokens (user_id, client_id, token_hash, token_type,
                    expiry_at, created_at, updated_at, token_updated_at,
                    token_subtype, last_token_hash, previous_token_hash, last_used_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            jdbcTemplate.update(sql,
                token.userId, token.client, token.token, token.tokenType,
                token.expiryAt, token.createdAt, token.updatedAt, token.tokenUpdatedAt,
                token.tokenSubtype, token.lastToken, token.previousToken, token.lastUsedAt
            )
            return token.copy(id = getLastInsertId())
        } else {
            // UPDATE
            val sql = """
                UPDATE tokens SET token_hash = ?, expiry_at = ?, updated_at = ?,
                    token_updated_at = ?, token_subtype = ?, last_token_hash = ?,
                    previous_token_hash = ?, last_used_at = ?
                WHERE id = ?
            """.trimIndent()

            jdbcTemplate.update(sql,
                token.token, token.expiryAt, token.updatedAt, token.tokenUpdatedAt,
                token.tokenSubtype, token.lastToken, token.previousToken, token.lastUsedAt,
                token.id
            )
            return token
        }
    }

    override fun findById(id: Long): Token? {
        // ... implement query
        return null
    }

    // ... implement other methods
}
```

## Service Configuration

### Create Your Token Service

```kotlin
import com.quantipixels.ogiri.security.tokens.OgiriTokenService
import com.quantipixels.ogiri.security.tokens.OgiriTokenRepository
import com.quantipixels.ogiri.security.tokens.TokenType
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TokenService(
    repository: OgiriTokenRepository<Token>,
    passwordEncoder: PasswordEncoder,
    userDirectory: OgiriUserDirectory,
    identifierPolicy: IdentifierPolicy,
    subTokenRegistry: SubTokenRegistry,
    properties: OgiriConfigurationProperties,
) : OgiriTokenService<Token>(
    repository,
    passwordEncoder,
    userDirectory,
    identifierPolicy,
    subTokenRegistry,
    properties,
) {
    /**
     * Factory method to create token instances.
     * Called by parent service during token creation.
     */
    override fun tokenFactory(
        userId: Long,
        client: String,
        hashedToken: String,
        tokenType: TokenType,
        expiry: Instant,
        tokenSubtype: String?,
    ): Token = Token(
        userId = userId,
        client = client,
        token = hashedToken,
        tokenType = tokenType.label,
        expiryAt = expiry,
        tokenSubtype = tokenSubtype,
    )
}
```

## User Directory Implementation

The `OgiriUserDirectory` provides user lookup for authentication.

```kotlin
import com.quantipixels.ogiri.security.spi.OgiriUserDirectory
import com.quantipixels.ogiri.security.spi.OgiriUser
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component

@Component
class UserDirectoryImpl(private val userRepository: UserRepository) : OgiriUserDirectory {

    override fun loadUserByUsername(username: String): OgiriUser? =
        userRepository.findByUsername(username)

    override fun findById(id: Long): OgiriUser? =
        userRepository.findById(id).orElse(null)

    override fun findByEmail(email: String): OgiriUser? =
        userRepository.findByEmail(email)

    override fun findByUsername(username: String): OgiriUser? =
        userRepository.findByUsername(username)

    override fun recordSuccessfulLogin(userId: Long) {
        val user = userRepository.findById(userId).orElse(null) ?: return
        user.lastLoginAt = Instant.now()
        userRepository.save(user)
    }
}
```

Your `User` entity should implement `OgiriUser`:

```kotlin
import com.quantipixels.ogiri.security.spi.OgiriUser
import org.springframework.security.core.authority.SimpleGrantedAuthority

@Entity
data class User(
    @Id @GeneratedValue
    override val userId: Long = 0,

    override var username: String = "",
    override var password: String = "",
    override var email: String = "",

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @ElementCollection
    var roles: Set<String> = emptySet(),
) : OgiriUser {
    override fun getOgiriUserId(): Long = userId

    override fun getAuthorities() = roles.map { SimpleGrantedAuthority(it) }

    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
}
```

## Integration Steps

### Step 1: Add Dependencies

```gradle
dependencies {
    implementation 'com.quantipixels:ogiri-security:1.2.0'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.postgresql:postgresql:14.0'
}
```

### Step 2: Configure Application Properties

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: password
  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect

ogiri:
  auth:
    max-clients: 10 # Max concurrent clients per user
    batch-grace-seconds: 5 # Grace period for batch requests
    token-lifespan-days: 14 # Token validity duration
  security:
    register-filter: true # Auto-register Ogiri filter
  cleanup:
    enabled: true
    interval-ms: 21600000 # Daily cleanup of expired tokens
```

### Step 3: Create Security Configuration

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
```

### Step 4: Define Public Routes

```kotlin
import com.quantipixels.ogiri.security.routes.OgiriRoute
import com.quantipixels.ogiri.security.routes.OgiriRouteRegistry
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component

@Component
class RouteRegistryImpl : OgiriRouteRegistry {

    override fun routes(): List<OgiriRoute> = listOf(
        OgiriRoute(HttpMethod.POST, "/api/auth/login", true, false, null),
        OgiriRoute(HttpMethod.GET, "/api/health", true, false, null),
        OgiriRoute(HttpMethod.GET, "/swagger-ui/**", true, false, null),
    )
}
```

### Step 5: Create a Login Endpoint

```kotlin
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val tokenService: TokenService) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): Map<String, Any> {
        val authHeader = try {
            tokenService.createNewAuthToken(
                userId = authenticateUser(request.username, request.password),
                client = request.deviceName
            )
        } catch (e: Exception) {
            return mapOf("error" to e.message)
        }

        // Append auth headers to response
        response.appendAuthHeaders(authHeader)

        return mapOf(
            "accessToken" to authHeader.accessToken,
            "client" to authHeader.client,
            "expiry" to authHeader.expiry
        )
    }

    private fun authenticateUser(username: String, password: String): Long {
        // Implement authentication logic
        return 1L
    }
}

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceName: String? = null,
)
```

## Testing

### Unit Tests with In-Memory Repository

```kotlin
import com.quantipixels.ogiri.security.testutil.InMemoryTokenRepository
import com.quantipixels.ogiri.security.testutil.TestToken
import org.junit.jupiter.api.Test

class TokenServiceTest {

    private val repository = InMemoryTokenRepository()
    private val passwordEncoder = MyPasswordEncoder()

    @Test
    fun `should create token for user`() {
        val service = TokenService(repository, passwordEncoder, /* ... */)

        val token = service.createNewAuthToken(userId = 1L, client = "web")

        assert(token.accessToken.isNotEmpty())
        assert(repository.getAllTokens().size == 1)
    }
}
```

### Integration Tests

```kotlin
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired lateinit var tokenService: TokenService
    @Autowired lateinit var tokenRepository: TokenRepository

    @Test
    fun `should authenticate user and create tokens`() {
        val authHeader = tokenService.createNewAuthToken(userId = 1L, client = "web")

        assert(authHeader.accessToken.isNotEmpty())
        assert(authHeader.subTokens?.isNotEmpty() == true)
    }
}
```

## Common Issues

### Issue: Token Not Persisting

**Cause:** Repository not implementing all `OgiriTokenRepository` methods

**Solution:** Ensure all interface methods are implemented. Use IDE code generation.

### Issue: Expired Tokens Not Being Cleaned

**Cause:** `cleanup.enabled` is false or cron schedule not firing

**Solution:** Check `application.yml` configuration and verify Spring task scheduling is enabled with `@EnableScheduling`.

### Issue: Token Rotation Not Working

**Cause:** `batch-grace-seconds` is too high

**Solution:** Adjust to an appropriate value (typically 5 seconds) to allow batch requests but still rotate tokens.

---

For more details, see:

- [Interface-First Design](interface-first-design.md)
- [Configuration Guide](configuration.md)
- [Database Integration](database.md)
