# Database Integration Guide

**ogiri** is completely database-agnostic. Token persistence is defined through the `TokenRepository<T>` interface, which you implement for your specific database and stack.

## Overview

The library does not depend on any specific database, ORM, or persistence framework. Your application has complete control over:

- **Database choice:** PostgreSQL, MySQL, MongoDB, Redis, DynamoDB, Cassandra, etc.
- **Persistence layer:** JPA/Hibernate, Spring Data, custom JDBC, native drivers
- **Migration strategy:** Flyway, Liquibase, custom scripts, or ORM auto-DDL
- **Connection pooling:** HikariCP, c3p0, Druid, or any other pooler
- **Schema design:** Customize tables, columns, indexes, and constraints

## Token Model Contract

Any token implementation must support these operations:

```kotlin
interface TokenRepository<T> {
  fun findByUserIdAndClient(userId: Long, client: String): T?
  fun deleteExpiredTokens(expiryBefore: Instant)
  fun save(token: T): T
  fun delete(token: T)
}
```

The token entity must have these fields:

| Field | Type | Purpose |
|-------|------|---------|
| `userId` | Long | User identifier |
| `client` | String | Application/client identifier |
| `token` | String | Token hash (BCrypt or similar) |
| `tokenType` | String | Token type: "APP" or custom (e.g., "chat", "device") |
| `expiryAt` | Instant | Token expiration timestamp (indexed) |
| `lastToken` | String (nullable) | Previous token hash (for rotation grace period) |
| `previousToken` | String (nullable) | Token before last (for extended grace period) |
| `tokenSubtype` | String (nullable) | Sub-token name/type |
| `createdAt` | Instant | When token was created |
| `updatedAt` | Instant | Last update timestamp |
| `tokenUpdatedAt` | Instant | When token/rotation last occurred |
| `lastUsedAt` | Instant (nullable) | When token was last used |

**Constraints:**
- Unique constraint on `(user_id, client_id)` – One token per user per client
- Index on `user_id` – Fast user lookups
- Index on `expiry_at` – Fast cleanup queries

## Implementation Patterns

### Pattern 1: JPA with BaseToken (SQL Databases)

**Best for:** PostgreSQL, MySQL, MariaDB, Oracle, H2, SQL Server

Use the provided `BaseToken` class with any JPA-compatible database:

*Kotlin*
```kotlin
// Use the default Token entity
@Repository
interface UserTokenRepository : JpaRepository<Token, Long>, TokenRepository<Token> {
  override fun findByUserIdAndClient(userId: Long, client: String): Token?
  override fun deleteExpiredTokens(expiryBefore: Instant)

  fun findByUserIdAndClientEquals(userId: Long, client: String): Token?
  fun deleteByExpiryAtBefore(expiryAt: Instant)
}
```

*Java*
```java
@Repository
public interface UserTokenRepository extends JpaRepository<Token, Long>, TokenRepository<Token> {
  @Override
  @Query("SELECT t FROM Token t WHERE t.userId = :userId AND t.client = :client")
  Token findByUserIdAndClient(@Param("userId") Long userId, @Param("client") String client);

  @Override
  @Modifying
  @Query("DELETE FROM Token t WHERE t.expiryAt < :expiryBefore")
  void deleteExpiredTokens(@Param("expiryBefore") Instant expiryBefore);

  Token findByUserIdAndClientEquals(Long userId, String client);

  void deleteByExpiryAtBefore(Instant expiryAt);
}
```

**Configuration:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: secret
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**Schema (PostgreSQL):**

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

  UNIQUE (user_id, client_id),
  INDEX idx_user_tokens_user_id (user_id),
  INDEX idx_user_tokens_expiry (expiry_at)
);
```

---

### Pattern 2: Custom JPA Entity with Table Mapping

**Best for:** Projects with existing token tables or custom naming conventions

Extend `BaseToken` with your own mappings:

*Kotlin*
```kotlin
@Entity
@Table(
  name = "app_security_tokens",
  uniqueConstraints = [
    UniqueConstraint(columnNames = ["user_id", "client_id"])
  ],
  indexes = [
    Index(columnList = "user_id"),
    Index(columnList = "expiry_at")
  ]
)
data class AppToken(
  userId: Long,
  client: String,
  token: String,
  tokenType: TokenType = TokenType.APP,
  expiryAt: Instant,
  lastToken: String? = null,
  previousToken: String? = null,
  tokenSubtype: String? = null,
) : BaseToken(userId, client, token, tokenType, expiryAt, lastToken, previousToken, tokenSubtype)

@Repository
interface AppTokenRepository : JpaRepository<AppToken, Long>, TokenRepository<AppToken> {
  override fun findByUserIdAndClient(userId: Long, client: String): AppToken?
  override fun deleteExpiredTokens(expiryBefore: Instant)

  fun findByUserIdAndClientEquals(userId: Long, client: String): AppToken?
  fun deleteByExpiryAtBefore(expiryAt: Instant)
}
```

*Java*
```java
@Entity
@Table(
  name = "app_security_tokens",
  uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "client_id"})
  },
  indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "expiry_at")
  }
)
public class AppToken extends BaseToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "client_id", nullable = false)
  private String client;

  // ... other fields

  public AppToken() {}

  public AppToken(Long userId, String client, String token, String tokenType,
                  Instant expiryAt, String lastToken, String previousToken,
                  String tokenSubtype) {
    super(userId, client, token, tokenType, expiryAt, lastToken, previousToken, tokenSubtype);
  }

  // Getters and setters...
}

@Repository
public interface AppTokenRepository extends JpaRepository<AppToken, Long>, TokenRepository<AppToken> {
  @Override
  @Query("SELECT t FROM AppToken t WHERE t.userId = :userId AND t.client = :client")
  AppToken findByUserIdAndClient(@Param("userId") Long userId, @Param("client") String client);

  @Override
  @Modifying
  @Query("DELETE FROM AppToken t WHERE t.expiryAt < :expiryBefore")
  void deleteExpiredTokens(@Param("expiryBefore") Instant expiryBefore);

  AppToken findByUserIdAndClientEquals(Long userId, String client);

  void deleteByExpiryAtBefore(Instant expiryAt);
}
```

---

### Pattern 3: NoSQL (MongoDB)

**Best for:** MongoDB, Firebase, DynamoDB

Implement `TokenRepository<T>` directly without extending `BaseToken`:

*Kotlin*
```kotlin
// Define your token model
data class MongoToken(
  val id: String = ObjectId().toString(),
  val userId: Long,
  val client: String,
  val token: String,
  val tokenType: String = "APP",
  val expiryAt: Instant,
  val lastToken: String? = null,
  val previousToken: String? = null,
  val tokenSubtype: String? = null,
  val createdAt: Instant = Instant.now(),
  val updatedAt: Instant = Instant.now(),
  val tokenUpdatedAt: Instant = Instant.now(),
  val lastUsedAt: Instant? = null,
)

// Implement TokenRepository
@Repository
class MongoTokenRepository(private val mongoTemplate: MongoTemplate) : TokenRepository<MongoToken> {

  override fun findByUserIdAndClient(userId: Long, client: String): MongoToken? {
    val query = Query(
      Criteria.where("userId").`is`(userId)
        .and("client").`is`(client)
    )
    return mongoTemplate.findOne(query, MongoToken::class.java)
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    val query = Query(Criteria.where("expiryAt").lt(expiryBefore))
    mongoTemplate.remove(query, MongoToken::class.java)
  }

  fun save(token: MongoToken): MongoToken =
    mongoTemplate.save(token)

  fun delete(token: MongoToken) =
    mongoTemplate.remove(
      Query(Criteria.where("_id").`is`(token.id)),
      MongoToken::class.java
    )
}
```

*Java*
```java
// Define your token model
public class MongoToken {
  @org.springframework.data.mongodb.core.mapping.Id
  private String id = new ObjectId().toString();
  private Long userId;
  private String client;
  private String token;
  private String tokenType = "APP";
  private Instant expiryAt;
  private String lastToken;
  private String previousToken;
  private String tokenSubtype;
  private Instant createdAt = Instant.now();
  private Instant updatedAt = Instant.now();
  private Instant tokenUpdatedAt = Instant.now();
  private Instant lastUsedAt;

  // Getters and setters...
}

// Implement TokenRepository
@Repository
public class MongoTokenRepository implements TokenRepository<MongoToken> {
  private final MongoTemplate mongoTemplate;

  public MongoTokenRepository(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public MongoToken findByUserIdAndClient(Long userId, String client) {
    Query query = new Query(
      Criteria.where("userId").is(userId)
        .and("client").is(client)
    );
    return mongoTemplate.findOne(query, MongoToken.class);
  }

  @Override
  public void deleteExpiredTokens(Instant expiryBefore) {
    Query query = new Query(Criteria.where("expiryAt").lt(expiryBefore));
    mongoTemplate.remove(query, MongoToken.class);
  }

  public MongoToken save(MongoToken token) {
    return mongoTemplate.save(token);
  }

  public void delete(MongoToken token) {
    Query query = new Query(Criteria.where("_id").is(token.getId()));
    mongoTemplate.remove(query, MongoToken.class);
  }
}
```

**Configuration:**

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/myapp
      database: myapp
```

---

### Pattern 4: Redis (Cache/Session Store)

**Best for:** High-performance token caching, temporary tokens

*Kotlin*
```kotlin
@Repository
class RedisTokenRepository(private val redisTemplate: RedisTemplate<String, Token>) : TokenRepository<Token> {

  override fun findByUserIdAndClient(userId: Long, client: String): Token? {
    val key = "token:$userId:$client"
    return redisTemplate.opsForValue().get(key)
  }

  fun save(token: Token): Token {
    val key = "token:${token.userId}:${token.client}"
    val ttl = Duration.between(Instant.now(), token.expiryAt)
    redisTemplate.opsForValue().set(key, token, ttl)
    return token
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    // Redis handles TTL expiration automatically
    // Manual cleanup optional for explicit control
  }

  fun delete(token: Token) {
    val key = "token:${token.userId}:${token.client}"
    redisTemplate.delete(key)
  }
}
```

*Java*
```java
@Repository
public class RedisTokenRepository implements TokenRepository<Token> {
  private final RedisTemplate<String, Token> redisTemplate;

  public RedisTokenRepository(RedisTemplate<String, Token> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Token findByUserIdAndClient(Long userId, String client) {
    String key = "token:" + userId + ":" + client;
    return redisTemplate.opsForValue().get(key);
  }

  public Token save(Token token) {
    String key = "token:" + token.getUserId() + ":" + token.getClient();
    Duration ttl = Duration.between(Instant.now(), token.getExpiryAt());
    redisTemplate.opsForValue().set(key, token, ttl);
    return token;
  }

  @Override
  public void deleteExpiredTokens(Instant expiryBefore) {
    // Redis handles TTL expiration automatically
    // Manual cleanup optional for explicit control
  }

  public void delete(Token token) {
    String key = "token:" + token.getUserId() + ":" + token.getClient();
    redisTemplate.delete(key);
  }
}
```

---

### Pattern 5: Adapter for Existing Token Table

**Best for:** Projects with existing token/session infrastructure

If your project already stores tokens in a different table or format, implement an adapter:

*Kotlin*
```kotlin
// Your existing token model
data class LegacyToken(
  val id: Long,
  val userId: Long,
  val sessionId: String,  // Instead of client
  val accessToken: String,  // Instead of token
  val expiresAt: LocalDateTime,
  // ... other fields
)

// Adapter to TokenRepository
@Repository
class LegacyTokenAdapter(private val legacyTokenService: LegacyTokenService) : TokenRepository<LegacyToken> {

  override fun findByUserIdAndClient(userId: Long, client: String): LegacyToken? {
    // Map ogiri's "client" to your "sessionId"
    return legacyTokenService.findByUserIdAndSessionId(userId, client)
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    val beforeLocalDateTime = LocalDateTime.ofInstant(expiryBefore, ZoneId.systemDefault())
    legacyTokenService.deleteExpiredBefore(beforeLocalDateTime)
  }

  fun save(token: LegacyToken): LegacyToken =
    legacyTokenService.save(token)

  fun delete(token: LegacyToken) =
    legacyTokenService.delete(token.id)
}

// Provide as a bean
@Configuration
class TokenRepositoryConfig {
  @Bean
  fun tokenRepository(legacyTokenService: LegacyTokenService) =
    LegacyTokenAdapter(legacyTokenService)
}
```

*Java*
```java
// Your existing token model
public class LegacyToken {
  private Long id;
  private Long userId;
  private String sessionId;      // Instead of client
  private String accessToken;    // Instead of token
  private LocalDateTime expiresAt;
  // ... other fields

  // Getters and setters...
}

// Adapter to TokenRepository
@Repository
public class LegacyTokenAdapter implements TokenRepository<LegacyToken> {
  private final LegacyTokenService legacyTokenService;

  public LegacyTokenAdapter(LegacyTokenService legacyTokenService) {
    this.legacyTokenService = legacyTokenService;
  }

  @Override
  public LegacyToken findByUserIdAndClient(Long userId, String client) {
    // Map ogiri's "client" to your "sessionId"
    return legacyTokenService.findByUserIdAndSessionId(userId, client);
  }

  @Override
  public void deleteExpiredTokens(Instant expiryBefore) {
    LocalDateTime beforeLocalDateTime = LocalDateTime.ofInstant(expiryBefore, ZoneId.systemDefault());
    legacyTokenService.deleteExpiredBefore(beforeLocalDateTime);
  }

  public LegacyToken save(LegacyToken token) {
    return legacyTokenService.save(token);
  }

  public void delete(LegacyToken token) {
    legacyTokenService.delete(token.getId());
  }
}

// Provide as a bean
@Configuration
public class TokenRepositoryConfig {
  @Bean
  public LegacyTokenAdapter tokenRepository(LegacyTokenService legacyTokenService) {
    return new LegacyTokenAdapter(legacyTokenService);
  }
}
```

---

## Schema Examples

Bundled schema files are provided in `ogiri-core/src/main/resources/ogiri/db/` and can be referenced from your migration tool or application initialization.

| Database | File | Usage |
|----------|------|-------|
| **PostgreSQL** | `ogiri-user-tokens.sql` | Default schema with SERIAL primary key and native timestamp handling |
| **MySQL/MariaDB** | `ogiri-user-tokens-mysql.sql` | MySQL-specific syntax with AUTO_INCREMENT and ON UPDATE CURRENT_TIMESTAMP |
| **H2 (In-Memory)** | `ogiri-user-tokens-h2.sql` | H2-compatible schema for testing and development |
| **MongoDB** | `ogiri-tokens-mongodb.js` | MongoDB collection setup with schema validation and TTL index |

### Referencing Bundled Schemas

**With Flyway:**
```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:/ogiri/db
```

**With Liquibase:**
```xml
<databaseChangeLog>
  <include file="classpath:/ogiri/db/ogiri-user-tokens.sql" relativeToChangelogFile="false" />
</databaseChangeLog>
```

**Via Spring Boot DataSourceInitializer:**
```yaml
spring:
  sql:
    init:
      data-locations: classpath:/ogiri/db/ogiri-user-tokens.sql
      mode: always
```

### Schema Structure

All bundled schemas define the same token model with database-specific syntax adjustments:

**Required Fields:**
- `id` – Auto-incrementing primary key
- `user_id` – User identifier (indexed)
- `client_id` – Client/app identifier
- `token_hash` – BCrypt or similar hash (NOT plaintext)
- `token_type` – Token classification (default: 'APP')
- `expiry_at` – Expiration timestamp (indexed, auto-cleanup via TTL)
- `token_subtype` – Optional sub-token identifier
- `created_at`, `updated_at`, `token_updated_at` – Audit timestamps
- `last_token_hash`, `previous_token_hash` – Grace period token history
- `last_used_at` – Last access timestamp (optional)

**Constraints:**
- Unique constraint on `(user_id, client_id)` – One token per user per client
- Indexes on `user_id` and `expiry_at` – Performance for lookups and cleanup

**For detailed schema content, see the bundled SQL/JavaScript files in `ogiri-core/src/main/resources/ogiri/db/`.**

---

## Migration Tools

ogiri does not manage migrations. Choose your tool:

### Flyway (SQL-based)

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:/ogiri/db
    baseline-on-migrate: true
```

Create `src/main/resources/db/migration/V1__create_tokens.sql`:

```sql
CREATE TABLE user_tokens (
  -- ... schema from examples above
);
```

### Liquibase (Declarative)

```yaml
spring:
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

Create `src/main/resources/db/changelog/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - sqlFile:
      path: classpath:/ogiri/db/ogiri-user-tokens.sql
      relativeToChangelogFile: false
  - changeSet:
      id: 2-add-custom-column
      author: your-name
      changes:
        - addColumn:
            tableName: user_tokens
            columns:
              - column:
                  name: custom_field
                  type: VARCHAR(255)
```

### Custom Scripts

Maintain your own SQL files in `src/main/resources/db/`:

```
src/main/resources/db/
├── init.sql
├── v1__create_tokens.sql
├── v2__add_indexes.sql
└── cleanup.sql
```

Execute via:
- Spring Boot initialization scripts (DataSourceInitializer)
- Custom Liquibase changelog
- Flyway migration
- Manual execution

---

## No Required Dependencies

ogiri adds **zero** database-specific dependencies to your classpath. Your application controls:

- `org.postgresql:postgresql` (PostgreSQL)
- `mysql:mysql-connector-java` (MySQL)
- `org.mongodb:mongodb-driver-core` (MongoDB)
- `redis.clients:jedis` (Redis)
- `com.h2database:h2` (H2)
- And any other driver/client your stack uses

---

## Best Practices

1. **Always use hashed tokens** – Never store plaintext tokens in the database
2. **Index expiry_at and user_id** – Enables fast cleanup and lookups
3. **Implement token rotation** – Leverage `lastToken` and `previousToken` for grace periods
4. **Clean up expired tokens** – The library provides cleanup job; ensure it runs
5. **Monitor token table size** – Archive old tokens if they accumulate
6. **Use connection pooling** – HikariCP is recommended for best performance
7. **Test with your database** – Different databases have different quirks

---

## Questions?

- See `README.md` for project overview
- Check `CLAUDE.md` for development guidelines
- Review `sample/sample-java/` or `sample/sample-kotlin/` for working examples
- Read `docs/AUTHENTICATION.md` for token flow details
