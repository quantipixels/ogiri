# Database Integration

Ogiri is database-agnostic. Two official adapter modules are available:

- **`ogiri-jpa`** — JPA/Hibernate (recommended for Spring Data users)
- **`ogiri-jdbc`** — Spring JDBC via `JdbcClient` (recommended when you want lightweight SQL without Hibernate)

## Quick Start with JPA (Recommended)

### 1. Add Dependency

```kotlin
implementation("com.quantipixels.ogiri:ogiri-jpa:{{ config.extra.ogiri_version }}")
```

This includes `ogiri-core` and `spring-boot-starter-data-jpa` transitively.

### 2. Create Your Token Entity

=== "Kotlin"

    ```kotlin
    @Entity
    @Table(
        name = "user_tokens",
        indexes = [
            Index(name = "idx_tokens_user_id", columnList = "user_id"),
            Index(name = "idx_tokens_expiry", columnList = "expiry_at")
        ],
        uniqueConstraints = [
            UniqueConstraint(name = "uk_tokens_user_client", columnNames = ["user_id", "client"])
        ]
    )
    class MyToken : OgiriBaseTokenEntity()
    ```

=== "Java"

    ```java
    @Entity
    @Table(
        name = "user_tokens",
        indexes = {
            @Index(name = "idx_tokens_user_id", columnList = "user_id"),
            @Index(name = "idx_tokens_expiry", columnList = "expiry_at")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_tokens_user_client", columnNames = {"user_id", "client"})
        }
    )
    public class MyToken extends OgiriBaseTokenEntity {
        public MyToken() {
            super();
        }
    }
    ```

`OgiriBaseTokenEntity` provides all fields with proper JPA annotations:

- `id`, `userId`, `client`, `token`, `tokenType`
- `expiryAt`, `tokenUpdatedAt`, `createdAt`, `updatedAt`
- `previousToken`, `lastToken`, `tokenSubtype`
- `lastUsedAt`, `plainToken` (transient)

### 3. Create Repository

Extend both `JpaRepository` and `OgiriTokenRepository` directly:

=== "Kotlin"

    ```kotlin
    @Repository
    interface MyTokenRepository :
        JpaRepository<MyToken, Long>, OgiriTokenRepository<MyToken> {

        @Query("SELECT COUNT(t) FROM MyToken t WHERE t.userId = :userId")
        override fun countByUserId(userId: Long): Long

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        override fun deleteByUserIdAndClient(userId: Long, client: String)

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client IN ?2")
        override fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>)

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1")
        override fun deleteByUserId(userId: Long)

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.expiryAt < ?1")
        override fun deleteByExpiryAtBefore(cutoff: Instant): Int
    }
    ```

=== "Java"

    ```java
    @Repository
    public interface MyTokenRepository
        extends JpaRepository<MyToken, Long>, OgiriTokenRepository<MyToken> {

        @Query("SELECT COUNT(t) FROM MyToken t WHERE t.userId = :userId")
        @Override
        long countByUserId(long userId);

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        @Override
        void deleteByUserIdAndClient(long userId, String client);

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client IN ?2")
        @Override
        void deleteByUserIdAndClientIn(long userId, Collection<String> clients);

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1")
        @Override
        void deleteByUserId(long userId);

        @Transactional @Modifying
        @Query("DELETE FROM MyToken t WHERE t.expiryAt < ?1")
        @Override
        int deleteByExpiryAtBefore(Instant cutoff);
    }
    ```

Spring Data auto-generates these from method names:

- `findByUserIdOrderByUpdatedAtDesc(userId)`
- `findByUserIdAndClient(userId, client)` &rarr; `Optional<T>`
- `findByUserIdAndClientIn(userId, clients)` &rarr; `List<T>`
- `findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, tokenSubtype)`
- `findByExpiryAtBefore(cutoff)`
- `findByTokenType(tokenType)`

### 4. Create Token Service

=== "Kotlin"

    ```kotlin
    @Service
    class MyTokenService(
        tokenRepository: OgiriTokenRepository<MyToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: IdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
        auditHookProvider: ObjectProvider<OgiriAuditHook>,
        rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
    ) : OgiriTokenService<MyToken>(
        tokenRepository, passwordEncoder, userDirectory,
        identifierPolicy, subTokenRegistry, properties,
        auditHookProvider, rateLimitHookProvider,
    ) {
        override fun tokenFactory(
            userId: Long, client: String, hashedToken: String,
            tokenType: OgiriTokenType, expiry: Instant,
            tokenSubtype: String?, plainTokenValue: String,
        ) = MyToken().apply {
            this.userId = userId
            this.client = client
            this.token = hashedToken
            this.tokenType = tokenType.name
            this.expiryAt = expiry
            this.tokenSubtype = tokenSubtype
            this.plainToken = plainTokenValue
        }
    }
    ```

=== "Java"

    ```java
    @Service
    public class MyTokenService extends OgiriTokenService<MyToken> {
        public MyTokenService(
            OgiriTokenRepository<MyToken> tokenRepository,
            PasswordEncoder passwordEncoder,
            OgiriUserDirectory userDirectory,
            IdentifierPolicy identifierPolicy,
            OgiriSubTokenRegistry subTokenRegistry,
            OgiriConfigurationProperties properties,
            ObjectProvider<OgiriAuditHook> auditHookProvider,
            ObjectProvider<OgiriRateLimitHook> rateLimitHookProvider
        ) {
            super(tokenRepository, passwordEncoder, userDirectory,
                  identifierPolicy, subTokenRegistry, properties,
                  auditHookProvider, rateLimitHookProvider);
        }

        @Override
        protected MyToken tokenFactory(
            Long userId, String client, String hashedToken,
            OgiriTokenType tokenType, Instant expiry,
            String tokenSubtype, String plainTokenValue
        ) {
            MyToken token = new MyToken();
            token.setUserId(userId);
            token.setClient(client);
            token.setToken(hashedToken);
            token.setTokenType(tokenType.name());
            token.setExpiryAt(expiry);
            token.setTokenSubtype(tokenSubtype);
            token.setPlainToken(plainTokenValue);
            return token;
        }
    }
    ```

---

## Quick Start with JDBC

Use `ogiri-jdbc` for Spring JDBC (`JdbcClient`) — no Hibernate, no `@Entity` annotations.

### 1. Add Dependency

```kotlin
implementation("com.quantipixels.ogiri:ogiri-jdbc:{{ config.extra.ogiri_version }}")
```

This includes `ogiri-core` and `spring-boot-starter-jdbc` transitively.

### 2. Create Your Token Row Class

=== "Kotlin"

    ```kotlin
    class MyToken : OgiriBaseTokenRow()
    ```

    No annotations needed. Extend `OgiriBaseTokenRow` and you're done.

=== "Java"

    ```java
    public class MyToken extends OgiriBaseTokenRow {
        public MyToken() {
            super(0L, 0L, "", "", "app",
                  Instant.now(), Instant.now(), Instant.now(), Instant.now());
        }
    }
    ```

    Java must call the primary constructor explicitly (no `@JvmOverloads` on the Kotlin `open class`).

`OgiriBaseTokenRow` provides all standard fields:

- `id`, `userId`, `client`, `token`, `tokenType`
- `expiryAt`, `tokenUpdatedAt`, `createdAt`, `updatedAt`
- `previousToken`, `lastToken`, `tokenSubtype`, `lastUsedAt`
- `plainToken` (transient)

### 3. Create Repository

Extend `OgiriJdbcTokenRepository<T>` and implement two methods:

=== "Kotlin"

    ```kotlin
    @Repository
    class MyTokenRepository(jdbcClient: JdbcClient) :
        OgiriJdbcTokenRepository<MyToken>(jdbcClient) {

        override fun tableName() = "user_tokens"

        override fun rowMapper() = RowMapper { rs, _ ->
            MyToken().apply {
                id = rs.getLong("id")
                userId = rs.getLong("user_id")
                client = rs.getString("client_id")
                token = rs.getString("token_hash")
                tokenType = rs.getString("token_type")
                tokenSubtype = rs.getString("token_subtype")
                expiryAt = rs.getTimestamp("expiry_at").toInstant()
                createdAt = rs.getTimestamp("created_at").toInstant()
                updatedAt = rs.getTimestamp("updated_at").toInstant()
                tokenUpdatedAt = rs.getTimestamp("token_updated_at").toInstant()
                lastToken = rs.getString("last_token_hash")
                previousToken = rs.getString("previous_token_hash")
                lastUsedAt = rs.getTimestamp("last_used_at")?.toInstant()
            }
        }
    }
    ```

=== "Java"

    ```java
    @Repository
    public class MyTokenRepository extends OgiriJdbcTokenRepository<MyToken> {

        public MyTokenRepository(JdbcClient jdbcClient) {
            super(jdbcClient);
        }

        @Override
        public String tableName() { return "user_tokens"; }

        @Override
        public RowMapper<MyToken> rowMapper() {
            return (rs, rowNum) -> {
                MyToken t = new MyToken();
                t.setId(rs.getLong("id"));
                t.setUserId(rs.getLong("user_id"));
                t.setClient(rs.getString("client_id"));
                t.setToken(rs.getString("token_hash"));
                t.setTokenType(rs.getString("token_type"));
                t.setTokenSubtype(rs.getString("token_subtype"));
                t.setExpiryAt(rs.getTimestamp("expiry_at").toInstant());
                t.setCreatedAt(rs.getTimestamp("created_at").toInstant());
                t.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
                t.setTokenUpdatedAt(rs.getTimestamp("token_updated_at").toInstant());
                t.setLastToken(rs.getString("last_token_hash"));
                t.setPreviousToken(rs.getString("previous_token_hash"));
                java.sql.Timestamp lastUsed = rs.getTimestamp("last_used_at");
                if (lastUsed != null) t.setLastUsedAt(lastUsed.toInstant());
                return t;
            };
        }
    }
    ```

`OgiriJdbcTokenRepository` auto-implements all 15 `OgiriTokenRepository` methods.

### 4. Create Token Service

=== "Kotlin"

    ```kotlin
    @Service
    class MyTokenService(
        tokenRepository: OgiriTokenRepository<MyToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: IdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties,
        auditHookProvider: ObjectProvider<OgiriAuditHook>,
        rateLimitHookProvider: ObjectProvider<OgiriRateLimitHook>,
    ) : OgiriTokenService<MyToken>(
        tokenRepository, passwordEncoder, userDirectory,
        identifierPolicy, subTokenRegistry, properties,
        auditHookProvider, rateLimitHookProvider,
    ) {
        override fun tokenFactory(
            userId: Long, client: String, hashedToken: String,
            tokenType: OgiriTokenType, expiry: Instant,
            tokenSubtype: String?, plainTokenValue: String,
        ) = MyToken().apply {
            this.userId = userId
            this.client = client
            this.token = hashedToken
            this.tokenType = tokenType.label
            this.expiryAt = expiry
            this.tokenSubtype = tokenSubtype
            this.plainToken = plainTokenValue
        }
    }
    ```

=== "Java"

    ```java
    @Service
    public class MyTokenService extends OgiriTokenService<MyToken> {
        public MyTokenService(
            OgiriTokenRepository<MyToken> tokenRepository,
            PasswordEncoder passwordEncoder,
            OgiriUserDirectory userDirectory,
            IdentifierPolicy identifierPolicy,
            OgiriSubTokenRegistry subTokenRegistry,
            OgiriConfigurationProperties properties,
            ObjectProvider<OgiriAuditHook> auditHookProvider,
            ObjectProvider<OgiriRateLimitHook> rateLimitHookProvider
        ) {
            super(tokenRepository, passwordEncoder, userDirectory,
                  identifierPolicy, subTokenRegistry, properties,
                  auditHookProvider, rateLimitHookProvider);
        }

        @Override
        protected MyToken tokenFactory(
            Long userId, String client, String hashedToken,
            OgiriTokenType tokenType, Instant expiry,
            String tokenSubtype, String plainTokenValue
        ) {
            MyToken token = new MyToken();
            token.setUserId(userId);
            token.setClient(client);
            token.setToken(hashedToken);
            token.setTokenType(tokenType.getLabel());
            token.setExpiryAt(expiry);
            token.setTokenSubtype(tokenSubtype);
            token.setPlainToken(plainTokenValue);
            return token;
        }
    }
    ```

!!! note "JDBC uses `tokenType.label` not `tokenType.name`"
`OgiriBaseTokenRow` stores the column value as `"app"` or `"sub"`, matching the SQL schema.
Use `tokenType.label` (Kotlin) or `tokenType.getLabel()` (Java) — not `.name` / `.name()`.

### 5. Configure Application

Exclude JPA auto-configuration and point Spring at the bundled schema:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: ${DB_USER}
    password: ${DB_PASS}
  sql:
    init:
      schema-locations: classpath:ogiri/db/ogiri-user-tokens.sql
      mode: always
```

### Switching Between JPA and JDBC (Spring Profiles)

The samples demonstrate profile-based switching. Annotate your JPA service with `@Profile("!jdbc")` and your JDBC service with `@Profile("jdbc")`, then activate via `--spring.profiles.active=jdbc`.

---

## Other Databases

For non-JPA databases, implement `OgiriTokenRepository<T>` directly using `ogiri-core`.

```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:{{ config.extra.ogiri_version }}")
```

### MongoDB

```kotlin
@Repository
class MongoTokenRepository(
    private val mongoTemplate: MongoTemplate
) : OgiriTokenRepository<MongoToken> {

    override fun <S : MongoToken> save(token: S): S = mongoTemplate.save(token)

    override fun findById(id: Long): Optional<MongoToken> =
        Optional.ofNullable(mongoTemplate.findById(id, MongoToken::class.java))

    override fun findByUserIdAndClient(userId: Long, client: String): Optional<MongoToken> {
        val query = Query(Criteria.where("userId").`is`(userId).and("client").`is`(client))
        return Optional.ofNullable(mongoTemplate.findOne(query, MongoToken::class.java))
    }

    override fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<MongoToken> {
        val query = Query(Criteria.where("userId").`is`(userId))
            .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
        return mongoTemplate.find(query, MongoToken::class.java)
    }

    override fun findByExpiryAtBefore(cutoff: Instant): List<MongoToken> {
        val query = Query(Criteria.where("expiryAt").lt(cutoff))
        return mongoTemplate.find(query, MongoToken::class.java)
    }

    override fun delete(token: MongoToken) {
        mongoTemplate.remove(Query(Criteria.where("_id").`is`(token.id)), MongoToken::class.java)
    }

    override fun deleteById(id: Long) {
        mongoTemplate.remove(Query(Criteria.where("_id").`is`(id)), MongoToken::class.java)
    }

    // ... implement remaining methods
}
```

### Redis

```kotlin
@Repository
class RedisTokenRepository(
    private val redisTemplate: RedisTemplate<String, Token>
) : OgiriTokenRepository<Token> {

    override fun findByUserIdAndClient(userId: Long, client: String): Optional<Token> =
        Optional.ofNullable(redisTemplate.opsForValue().get("token:$userId:$client"))

    override fun <S : Token> save(token: S): S {
        val key = "token:${token.userId}:${token.client}"
        val ttl = Duration.between(Instant.now(), token.expiryAt)
        if (ttl.isNegative || ttl.isZero) return token
        redisTemplate.opsForValue().set(key, token, ttl)
        return token
    }

    override fun findByExpiryAtBefore(cutoff: Instant): List<Token> =
        emptyList() // Redis handles TTL automatically

    override fun delete(token: Token) {
        redisTemplate.delete("token:${token.userId}:${token.client}")
    }

    // ... implement remaining methods
}
```

---

## Token Model Requirements

| Field            | Type    | Required | Description                     |
| ---------------- | ------- | -------- | ------------------------------- |
| `id`             | Long    | Yes      | Primary key                     |
| `userId`         | Long    | Yes      | User identifier                 |
| `client`         | String  | Yes      | Client/device identifier        |
| `token`          | String  | Yes      | BCrypt hash                     |
| `tokenType`      | String  | Yes      | "app" or "sub"                  |
| `expiryAt`       | Instant | Yes      | Expiration timestamp            |
| `createdAt`      | Instant | Yes      | Creation timestamp              |
| `updatedAt`      | Instant | Yes      | Last update                     |
| `tokenUpdatedAt` | Instant | Yes      | Last rotation                   |
| `lastToken`      | String  | No       | Previous token (rotation grace) |
| `previousToken`  | String  | No       | Token before last               |
| `tokenSubtype`   | String  | No       | Sub-token name                  |
| `lastUsedAt`     | Instant | No       | Last access                     |

**Constraints:**

- Unique constraint on `(userId, client)`
- Index on `userId` and `expiryAt`

---

## Bundled SQL Schemas

Schemas are bundled in `ogiri-core/src/main/resources/ogiri/db/`:

| Database   | File                          |
| ---------- | ----------------------------- |
| PostgreSQL | `ogiri-user-tokens.sql`       |
| MySQL      | `ogiri-user-tokens-mysql.sql` |
| H2         | `ogiri-user-tokens-h2.sql`    |

### Using with Flyway

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:/ogiri/db
```

### PostgreSQL Schema

```sql
CREATE TABLE user_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    token_type VARCHAR(20) NOT NULL,
    token_subtype VARCHAR(64),
    expiry_at TIMESTAMP(6) NOT NULL,
    previous_token_hash VARCHAR(255),
    last_token_hash VARCHAR(255),
    token_updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_user_tokens_user_client ON user_tokens (user_id, client_id);
CREATE INDEX idx_user_tokens_user_id ON user_tokens (user_id);
CREATE INDEX idx_user_tokens_expiry ON user_tokens (expiry_at);
CREATE INDEX idx_user_tokens_user_subtype ON user_tokens (user_id, token_subtype);
```

## Recommended Indexes

| Index                          | Columns                  | Purpose                 |
| ------------------------------ | ------------------------ | ----------------------- |
| `idx_user_tokens_user_id`      | `user_id`                | User token lookups      |
| `idx_user_tokens_expiry`       | `expiry_at`              | Cleanup job performance |
| `uk_user_tokens_user_client`   | `user_id, client_id`     | Unique token lookup     |
| `idx_user_tokens_user_subtype` | `user_id, token_subtype` | Sub-token queries       |

## Repository Methods

The `OgiriTokenRepository<T>` interface includes the following methods:

### Core CRUD

| Method                      | Description            |
| --------------------------- | ---------------------- |
| `save(token): T`            | Create or update token |
| `findById(id): Optional<T>` | Find by primary key    |
| `delete(token)`             | Delete token           |
| `deleteById(id)`            | Delete by primary key  |

### Query Methods

| Method                                                                      | Description                             |
| --------------------------------------------------------------------------- | --------------------------------------- |
| `findByUserIdOrderByUpdatedAtDesc(userId): List<T>`                         | All tokens for a user, newest first     |
| `findByUserIdAndClient(userId, client): Optional<T>`                        | Token for a specific user+client        |
| `findByUserIdAndClientIn(userId, clients): List<T>`                         | Batch fetch tokens for multiple clients |
| `findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype): List<T>` | Sub-tokens by type                      |
| `findByExpiryAtBefore(cutoff): List<T>`                                     | Expired tokens for cleanup              |
| `findByTokenType(tokenType): List<T>`                                       | Tokens by type                          |
| `countByUserId(userId): Long`                                               | Count tokens for a user                 |

### Delete Methods

| Method                                       | Description                      |
| -------------------------------------------- | -------------------------------- |
| `deleteByUserIdAndClient(userId, client)`    | Single-device logout             |
| `deleteByUserIdAndClientIn(userId, clients)` | Bulk session revocation          |
| `deleteByUserId(userId)`                     | Global logout / account deletion |
| `deleteByExpiryAtBefore(cutoff): Int`        | Cleanup expired tokens           |

---

## Best Practices

1. **Use `ogiri-jpa`** - Reduces boilerplate by ~70% for JPA users
2. **Always hash tokens** - Never store plaintext tokens
3. **Index `expiryAt`** - Enables fast cleanup queries
4. **Use connection pooling** - HikariCP recommended for production
5. **Monitor table size** - Archive old tokens if needed
6. **Test with your database** - Different databases have quirks
7. **Override `countByUserId`** - Use `@Query` for performance instead of loading all tokens
