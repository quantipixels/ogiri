# Database Integration

Ògiri is database-agnostic. Implement `TokenRepository<T>` for your database of choice.

## Token Model

Your token entity must support these fields:

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `userId` | Long | Yes | User identifier |
| `client` | String | Yes | Client/app identifier |
| `token` | String | Yes | BCrypt hash |
| `tokenType` | String | Yes | "APP" or sub-token name |
| `expiryAt` | Instant | Yes | Expiration timestamp |
| `lastToken` | String | No | Previous token (rotation grace) |
| `previousToken` | String | No | Token before last |
| `tokenSubtype` | String | No | Sub-token name |
| `createdAt` | Instant | Yes | Creation timestamp |
| `updatedAt` | Instant | Yes | Last update |
| `tokenUpdatedAt` | Instant | Yes | Last rotation |
| `lastUsedAt` | Instant | No | Last access |

**Constraints:**
- Unique constraint on `(userId, client)`
- Index on `userId` and `expiryAt`

## Implementation Patterns

### JPA (Recommended for SQL)

Use the built-in `Token` entity:

```kotlin
@Repository
interface MyTokenRepository : JpaRepository<Token, Long>, TokenRepository<Token>
```

<details>
<summary>Java version</summary>

```java
@Repository
public interface MyTokenRepository extends JpaRepository<Token, Long>, TokenRepository<Token> {}
```
</details>

### Custom JPA Entity

Extend `BaseToken` for custom table names:

```kotlin
@Entity
@Table(name = "app_tokens")
data class AppToken(
  userId: Long,
  client: String,
  token: String,
  expiryAt: Instant,
  // ... other fields
) : BaseToken(userId, client, token, TokenType.APP, expiryAt)

@Repository
interface AppTokenRepository : JpaRepository<AppToken, Long>, TokenRepository<AppToken>
```

### MongoDB

Implement `TokenRepository<T>` directly:

```kotlin
@Repository
class MongoTokenRepository(private val mongoTemplate: MongoTemplate) : TokenRepository<MongoToken> {

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
class RedisTokenRepository(private val redisTemplate: RedisTemplate<String, Token>) : TokenRepository<Token> {

  override fun findByUserIdAndClient(userId: Long, client: String): Token? {
    return redisTemplate.opsForValue().get("token:$userId:$client")
  }

  override fun save(token: Token): Token {
    val key = "token:${token.userId}:${token.client}"
    val ttl = Duration.between(Instant.now(), token.expiryAt)
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
class LegacyTokenAdapter(private val legacyService: LegacyTokenService) : TokenRepository<LegacyToken> {

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

| Database | File |
|----------|------|
| PostgreSQL | `ogiri-user-tokens.sql` |
| MySQL | `ogiri-user-tokens-mysql.sql` |
| H2 | `ogiri-user-tokens-h2.sql` |
| MongoDB | `ogiri-tokens-mongodb.js` |

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
  token_type VARCHAR(50) NOT NULL DEFAULT 'APP',
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

CREATE INDEX idx_user_tokens_user_id ON user_tokens (user_id);
CREATE INDEX idx_user_tokens_expiry ON user_tokens (expiry_at);
```

## Best Practices

1. **Always hash tokens** - Never store plaintext
2. **Index expiry_at** - Enables fast cleanup queries
3. **Use connection pooling** - HikariCP recommended
4. **Monitor table size** - Archive old tokens if needed
5. **Test with your database** - Different databases have quirks
