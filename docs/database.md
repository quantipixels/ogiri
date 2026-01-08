# Database Integration

Ògiri is database-agnostic. For JPA/Hibernate users, the `ogiri-jpa` module provides ready-to-use base classes that reduce boilerplate by ~70%.

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

That's it! `OgiriBaseTokenEntity` provides all 15+ fields with proper JPA annotations:

- `id`, `userId`, `client`, `token`, `tokenType`
- `expiryAt`, `tokenUpdatedAt`, `createdAt`, `updatedAt`
- `previousToken`, `lastToken`, `tokenPrefix`, `tokenSubtype`
- `lastUsedAt`, `plainToken` (transient)

### 3. Create JPA Repository Interface

=== "Kotlin"

    ```kotlin
    interface MyTokenJpaRepository : JpaRepository<MyToken, Long> {
        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC")
        fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<MyToken>

        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        fun findByUserIdAndClient(userId: Long, client: String): MyToken?

        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 ORDER BY t.updatedAt DESC")
        fun findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId: Long, subtype: String): List<MyToken>

        @Query("SELECT t FROM MyToken t WHERE t.expiryAt < ?1")
        fun findByExpiryAtBefore(cutoff: Instant): List<MyToken>

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        fun deleteByUserIdAndClient(userId: Long, client: String)

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client IN ?2")
        fun deleteByUserIdAndClientIn(userId: Long, clients: Collection<String>)

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1")
        fun deleteByUserId(userId: Long)
    }
    ```

=== "Java"

    ```java
    public interface MyTokenJpaRepository extends JpaRepository<MyToken, Long> {
        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 ORDER BY t.updatedAt DESC")
        List<MyToken> findByUserIdOrderByUpdatedAtDesc(Long userId);

        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        Optional<MyToken> findByUserIdAndClient(Long userId, String client);

        @Query("SELECT t FROM MyToken t WHERE t.userId = ?1 AND t.tokenSubtype = ?2 ORDER BY t.updatedAt DESC")
        List<MyToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(Long userId, String subtype);

        @Query("SELECT t FROM MyToken t WHERE t.expiryAt < ?1")
        List<MyToken> findByExpiryAtBefore(Instant cutoff);

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client = ?2")
        void deleteByUserIdAndClient(Long userId, String client);

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1 AND t.client IN ?2")
        void deleteByUserIdAndClientIn(Long userId, Collection<String> clients);

        @Modifying @Transactional
        @Query("DELETE FROM MyToken t WHERE t.userId = ?1")
        void deleteByUserId(Long userId);
    }
    ```

### 4. Create Repository Adapter

=== "Kotlin"

    ```kotlin
    @Repository
    @Primary
    class MyTokenRepositoryAdapter(
        jpaRepository: MyTokenJpaRepository
    ) : AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository>(jpaRepository) {

        override fun findByUserIdOrderByUpdatedAtDesc(userId: Long) =
            jpaRepository.findByUserIdOrderByUpdatedAtDesc(userId)

        override fun findByUserIdAndClientEquals(userId: Long, client: String) =
            jpaRepository.findByUserIdAndClient(userId, client)

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

=== "Java"

    ```java
    @Repository
    @Primary
    public class MyTokenRepositoryAdapter
        extends AbstractJpaTokenRepositoryAdapter<MyToken, MyTokenJpaRepository> {

        public MyTokenRepositoryAdapter(MyTokenJpaRepository jpaRepository) {
            super(jpaRepository);
        }

        @Override
        protected List<MyToken> findByUserIdOrderByUpdatedAtDesc(long userId) {
            return getJpaRepository().findByUserIdOrderByUpdatedAtDesc(userId);
        }

        @Override
        protected MyToken findByUserIdAndClientEquals(long userId, String client) {
            return getJpaRepository().findByUserIdAndClient(userId, client).orElse(null);
        }

        @Override
        protected List<MyToken> findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(long userId, String subtype) {
            return getJpaRepository().findByUserIdAndTokenSubtypeOrderByUpdatedAtDesc(userId, subtype);
        }

        @Override
        protected List<MyToken> findByExpiryAtBeforeCutoff(Instant cutoff) {
            return getJpaRepository().findByExpiryAtBefore(cutoff);
        }

        @Override
        protected void deleteByUserIdAndClientEquals(long userId, String client) {
            getJpaRepository().deleteByUserIdAndClient(userId, client);
        }

        @Override
        protected void deleteByUserIdAndClientIdIn(long userId, Collection<String> clientIds) {
            getJpaRepository().deleteByUserIdAndClientIn(userId, clientIds);
        }

        @Override
        protected void deleteByUserIdJpa(long userId) {
            getJpaRepository().deleteByUserId(userId);
        }
    }
    ```

### 5. Create Token Service

=== "Kotlin"

    ```kotlin
    @Service
    @Primary
    class MyTokenService(
        tokenRepository: OgiriTokenRepository<MyToken>,
        passwordEncoder: PasswordEncoder,
        userDirectory: OgiriUserDirectory,
        identifierPolicy: IdentifierPolicy,
        subTokenRegistry: OgiriSubTokenRegistry,
        properties: OgiriConfigurationProperties
    ) : OgiriTokenService<MyToken>(
        tokenRepository, passwordEncoder, userDirectory,
        identifierPolicy, subTokenRegistry, properties
    ) {
        override fun tokenFactory(
            userId: Long, client: String, hashedToken: String,
            tokenType: OgiriTokenType, expiry: Instant,
            tokenSubtype: String?, plainTokenValue: String
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
    @Primary
    public class MyTokenService extends OgiriTokenService<MyToken> {
        public MyTokenService(
            OgiriTokenRepository<MyToken> tokenRepository,
            PasswordEncoder passwordEncoder,
            OgiriUserDirectory userDirectory,
            IdentifierPolicy identifierPolicy,
            OgiriSubTokenRegistry subTokenRegistry,
            OgiriConfigurationProperties properties
        ) {
            super(tokenRepository, passwordEncoder, userDirectory,
                  identifierPolicy, subTokenRegistry, properties);
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

    override fun save(token: MongoToken): MongoToken = mongoTemplate.save(token)

    override fun findById(id: Long): MongoToken? =
        mongoTemplate.findById(id, MongoToken::class.java)

    override fun findByUserIdAndClient(userId: Long, client: String): MongoToken? {
        val query = Query(Criteria.where("userId").`is`(userId).and("client").`is`(client))
        return mongoTemplate.findOne(query, MongoToken::class.java)
    }

    override fun findAllByUserId(userId: Long): List<MongoToken> {
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

    // ... implement remaining methods
}
```

### Redis

```kotlin
@Repository
class RedisTokenRepository(
    private val redisTemplate: RedisTemplate<String, Token>
) : OgiriTokenRepository<Token> {

    override fun findByUserIdAndClient(userId: Long, client: String): Token? =
        redisTemplate.opsForValue().get("token:$userId:$client")

    override fun save(token: Token): Token {
        val key = "token:${token.userId}:${token.client}"
        val ttl = Duration.between(Instant.now(), token.expiryAt)
        if (ttl.isNegative || ttl.isZero) {
            return token // Don't store already-expired tokens
        }
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

### Legacy/Existing Token Table

```kotlin
@Repository
class LegacyTokenAdapter(
    private val legacyService: LegacyTokenService
) : OgiriTokenRepository<LegacyToken> {

    override fun findByUserIdAndClient(userId: Long, client: String) =
        legacyService.findByUserAndSession(userId, client)

    override fun save(token: LegacyToken) = legacyService.save(token)

    override fun delete(token: LegacyToken) = legacyService.delete(token.id)

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
| `tokenType`      | String  | Yes      | "APP" or sub-token name         |
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
    client VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    token_type VARCHAR(50) NOT NULL DEFAULT 'app',
    token_prefix VARCHAR(8),           -- For O(1) token lookup (v1.3.0+)
    expiry_at TIMESTAMP NOT NULL,
    last_token VARCHAR(255),
    previous_token VARCHAR(255),
    token_subtype VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    token_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMP,

    UNIQUE (user_id, client)
);

-- Required indexes
CREATE INDEX idx_user_tokens_user_id ON user_tokens (user_id);
CREATE INDEX idx_user_tokens_expiry ON user_tokens (expiry_at);

-- Performance optimization: Enables O(1) token lookup instead of O(n) BCrypt comparisons
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix)
  WHERE token_type = 'app' AND expiry_at > NOW();
```

## Recommended Indexes

For optimal performance, ensure these indexes exist:

| Index                          | Columns                  | Purpose                 |
| ------------------------------ | ------------------------ | ----------------------- |
| `idx_user_tokens_user_id`      | `user_id`                | User token lookups      |
| `idx_user_tokens_expiry`       | `expiry_at`              | Cleanup job performance |
| `idx_user_tokens_prefix`       | `token_prefix`           | O(1) token validation   |
| `idx_user_tokens_user_client`  | `user_id, client_id`     | Unique token lookup     |
| `idx_user_tokens_user_subtype` | `user_id, token_subtype` | Sub-token queries       |

### Adding Token Prefix Index (Migration)

For existing databases, add the `token_prefix` column and index:

```sql
-- PostgreSQL
ALTER TABLE user_tokens ADD COLUMN token_prefix VARCHAR(8);
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix)
  WHERE token_type = 'app' AND expiry_at > NOW();

-- MySQL
ALTER TABLE user_tokens ADD COLUMN token_prefix VARCHAR(8);
CREATE INDEX idx_user_tokens_prefix ON user_tokens (token_prefix);

-- Note: Existing tokens will have NULL token_prefix.
-- The token service falls back to scanning all tokens when prefix is NULL,
-- ensuring backwards compatibility. New tokens will populate this field automatically.
```

## Repository Methods

The `OgiriTokenRepository<T>` interface includes the following methods:

### Core Methods (Required)

| Method                                  | Description                      |
| --------------------------------------- | -------------------------------- |
| `findByUserIdAndClient(userId, client)` | Find token by user and client ID |
| `save(token)`                           | Create or update token           |
| `delete(token)`                         | Delete token                     |
| `deleteExpiredTokens(expiryBefore)`     | Delete all expired tokens        |

### Performance Optimization Methods (v1.3.0+)

These methods have default implementations but can be overridden for database-specific optimization:

| Method                                  | Description                         | Default Behavior                       |
| --------------------------------------- | ----------------------------------- | -------------------------------------- |
| `findValidTokensByPrefix(prefix, now)`  | O(1) token lookup by prefix         | Falls back to scanning all user tokens |
| `countByUserId(userId)`                 | Count active tokens for user        | Loads all tokens and counts            |
| `deleteExpiredBatch(cutoff, batchSize)` | Batched deletion for large datasets | Calls `deleteExpiredTokens`            |

**Example: Optimized PostgreSQL Implementation**

```kotlin
@Repository
interface AppTokenRepository : JpaRepository<AppToken, Long>, OgiriTokenRepository<AppToken> {

  @Query("""
    SELECT t FROM AppToken t
    WHERE t.tokenPrefix = :prefix
      AND t.expiryAt > :now
      AND t.tokenType = 'app'
  """)
  override fun findValidTokensByPrefix(prefix: String, now: Instant): List<AppToken>

  @Query("SELECT COUNT(t) FROM AppToken t WHERE t.userId = :userId")
  override fun countByUserId(userId: Long): Long

  @Modifying
  @Query("""
    DELETE FROM AppToken t
    WHERE t.expiryAt < :cutoff
    LIMIT :batchSize
  """)
  override fun deleteExpiredBatch(cutoff: Instant, batchSize: Int): Int
}
```

---

## Best Practices

1. **Use `ogiri-jpa`** - Reduces boilerplate by ~70% for JPA users
2. **Always hash tokens** - Never store plaintext tokens
3. **Index `expiryAt`** - Enables fast cleanup queries
4. **Index `token_prefix`** - Enables O(1) token lookups (v1.3.0+)
5. **Use connection pooling** - HikariCP recommended for production
6. **Monitor table size** - Archive old tokens if needed
7. **Test with your database** - Different databases have quirks
8. **Override performance methods** - Provide database-specific optimizations
