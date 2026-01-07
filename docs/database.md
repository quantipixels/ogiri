# Database Integration

Ògiri is database-agnostic. Implement `OgiriTokenRepository<T>` for your database of choice.

## Token Model

Your token entity must support these fields:

| Field            | Type    | Required | Description                             |
| ---------------- | ------- | -------- | --------------------------------------- |
| `userId`         | Long    | Yes      | User identifier                         |
| `clientId`       | String  | Yes      | Client/app identifier                   |
| `tokenHash`      | String  | Yes      | BCrypt hash                             |
| `tokenType`      | String  | Yes      | "app" or "sub"                          |
| `tokenPrefix`    | String  | No       | First 8 chars for O(1) lookup (v1.3.0+) |
| `expiryAt`       | Instant | Yes      | Expiration timestamp                    |
| `lastToken`      | String  | No       | Previous token (rotation grace)         |
| `previousToken`  | String  | No       | Token before last                       |
| `tokenSubtype`   | String  | No       | Sub-token name                          |
| `createdAt`      | Instant | Yes      | Creation timestamp                      |
| `updatedAt`      | Instant | Yes      | Last update                             |
| `tokenUpdatedAt` | Instant | Yes      | Last rotation                           |
| `lastUsedAt`     | Instant | No       | Last access                             |

**Constraints:**

- Unique constraint on `(userId, client)`
- Index on `userId` and `expiryAt`
- Index on `tokenPrefix` for O(1) token lookup (recommended)

## Implementation Patterns

### JPA (Recommended for SQL)

Use the built-in `Token` entity:

=== "Kotlin"

    ```kotlin
    @Repository
    interface MyTokenRepository : JpaRepository<Token, Long>, OgiriTokenRepository<Token>
    ```

=== "Java"

    ```java
    @Repository
    public interface MyTokenRepository extends JpaRepository<Token, Long>, OgiriTokenRepository<Token> {}
    ```

### Custom JPA Entity

Extend `OgiriBaseToken` for custom table names:

```kotlin
@Entity
@Table(name = "app_tokens")
data class AppToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    override val id: Long = 0,
    override val userId: Long,
    override val client: String,
    override var token: String,
    override val tokenType: String = "app",
    override var expiryAt: Instant,
    @CreationTimestamp override val createdAt: Instant = Instant.now(),
    @UpdateTimestamp override val updatedAt: Instant = Instant.now(),
    override var tokenUpdatedAt: Instant = Instant.now(),
) : OgiriBaseToken()

@Repository
interface AppTokenRepository : JpaRepository<AppToken, Long>, OgiriTokenRepository<AppToken>
```

### MongoDB

Implement `OgiriTokenRepository<T>` directly:

```kotlin
@Repository
class MongoTokenRepository(private val mongoTemplate: MongoTemplate) : OgiriTokenRepository<MongoToken> {

  override fun findByUserIdAndClient(userId: Long, client: String): MongoToken? {
    val query = Query(Criteria.where("userId").`is`(userId).and("client").`is`(client))
    return mongoTemplate.findOne(query, MongoToken::class.java)
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    val query = Query(Criteria.where("expiryAt").lt(expiryBefore))
    mongoTemplate.remove(query, MongoToken::class.java)
  }

  override fun save(token: MongoToken): MongoToken = mongoTemplate.save(token)

  override fun delete(token: MongoToken) {
    mongoTemplate.remove(Query(Criteria.where("_id").`is`(token.id)), MongoToken::class.java)
  }
}
```

### Redis

```kotlin
@Repository
class RedisTokenRepository(private val redisTemplate: RedisTemplate<String, Token>) : OgiriTokenRepository<Token> {

  override fun findByUserIdAndClient(userId: Long, client: String): Token? {
    return redisTemplate.opsForValue().get("token:$userId:$client")
  }

  override fun save(token: Token): Token {
    val key = "token:${token.userId}:${token.client}"
    val ttl = Duration.between(Instant.now(), token.expiryAt)
    if (ttl.isNegative || ttl.isZero) {
      return token // Don't store already-expired tokens
    }
    redisTemplate.opsForValue().set(key, token, ttl)
    return token
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    // Redis handles TTL automatically
  }

  override fun delete(token: Token) {
    redisTemplate.delete("token:${token.userId}:${token.client}")
  }
}
```

### Existing Token Table

Adapt an existing table:

```kotlin
@Repository
class LegacyTokenAdapter(private val legacyService: LegacyTokenService) : OgiriTokenRepository<LegacyToken> {

  override fun findByUserIdAndClient(userId: Long, client: String) =
    legacyService.findByUserIdAndSessionId(userId, client)

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    legacyService.deleteExpiredBefore(expiryBefore.atZone(ZoneId.systemDefault()).toLocalDateTime())
  }

  override fun save(token: LegacyToken) = legacyService.save(token)

  override fun delete(token: LegacyToken) = legacyService.delete(token.id)
}
```

## Bundled Schemas

Schemas are bundled in `ogiri-core/src/main/resources/ogiri/db/`:

| Database   | File                          |
| ---------- | ----------------------------- |
| PostgreSQL | `ogiri-user-tokens.sql`       |
| MySQL      | `ogiri-user-tokens-mysql.sql` |
| H2         | `ogiri-user-tokens-h2.sql`    |
| MongoDB    | `ogiri-tokens-mongodb.js`     |

### Using with Flyway

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:/ogiri/db
```

### Using with Liquibase

```xml
<databaseChangeLog>
  <include file="classpath:/ogiri/db/ogiri-user-tokens.sql"/>
</databaseChangeLog>
```

### PostgreSQL Schema

```sql
CREATE TABLE user_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  client_id VARCHAR(255) NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  token_type VARCHAR(50) NOT NULL DEFAULT 'app',
  token_prefix VARCHAR(8),           -- For O(1) token lookup (v1.3.0+)
  expiry_at TIMESTAMP NOT NULL,
  last_token_hash VARCHAR(255),
  previous_token_hash VARCHAR(255),
  token_subtype VARCHAR(50),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  token_updated_at TIMESTAMP NOT NULL,
  last_used_at TIMESTAMP,

  UNIQUE (user_id, client_id)
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

## Best Practices

1. **Always hash tokens** - Never store plaintext
2. **Index expiry_at** - Enables fast cleanup queries
3. **Index token_prefix** - Enables O(1) token lookups (v1.3.0+)
4. **Use connection pooling** - HikariCP recommended
5. **Monitor table size** - Archive old tokens if needed
6. **Test with your database** - Different databases have quirks
7. **Override performance methods** - Provide database-specific optimizations
