# Ogiri Sample Applications

This directory contains minimal sample Spring Boot applications demonstrating how to integrate the ogiri token-based authentication library in both Java and Kotlin.

## Sample Applications

### sample-java

A minimal Spring Boot application written in pure Java demonstrating:
- Java-only integration without Kotlin
- TokenUserDirectory implementation
- RouteRegistry configuration
- TokenRepository setup
- Basic REST endpoints with authentication

**Key Files:**
- `Application.java` – Spring Boot entry point
- `config/SecurityConfig.java` – Password encoder configuration
- `security/SampleTokenUserDirectory.java` – User directory implementation
- `security/SampleRouteRegistry.java` – Route registry
- `repository/SampleTokenRepository.java` – Token persistence
- `controller/HealthController.java` – Sample endpoints

**To run:**
```bash
./gradlew :sample:sample-java:bootRun
```

### sample-kotlin

A minimal Spring Boot application written in Kotlin demonstrating:
- Kotlin integration with Spring Boot
- TokenUserDirectory implementation in Kotlin
- RouteRegistry configuration
- TokenRepository setup
- Basic REST endpoints with authentication

**Key Files:**
- `Application.kt` – Spring Boot entry point
- `config/SecurityConfig.kt` – Password encoder configuration
- `security/SampleTokenUserDirectory.kt` – User directory implementation
- `security/SampleRouteRegistry.kt` – Route registry
- `repository/SampleTokenRepository.kt` – Token persistence
- `controller/HealthController.kt` – Sample endpoints

**To run:**
```bash
./gradlew :sample:sample-kotlin:bootRun
```

## Prerequisites

- Java 17 or later
- PostgreSQL database (local or Docker)
- Gradle 7.0+

## Database Setup

The samples use PostgreSQL by default, but ogiri works with any database. Adapt these instructions to your database of choice.

### PostgreSQL (Default)

Create the database:

```bash
# Using psql directly
createdb ogiri_sample

# Or using Docker
docker run --name ogiri-db -e POSTGRES_PASSWORD=postgres -d -p 5432:5432 postgres:15
docker exec ogiri-db createdb -U postgres ogiri_sample
```

Import the bundled schema:

```bash
psql -U postgres -d ogiri_sample < ../../ogiri-core/src/main/resources/ogiri/db/ogiri-user-tokens.sql
```

### MySQL/MariaDB

Create the database:

```bash
mysql -u root -p -e "CREATE DATABASE ogiri_sample;"
```

Create the schema (adapt `ogiri-user-tokens.sql` for MySQL syntax):

```sql
CREATE TABLE user_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  client_id VARCHAR(255) NOT NULL,
  token_hash VARCHAR(255) NOT NULL,
  token_type VARCHAR(50) NOT NULL DEFAULT 'APP',
  expiry_at TIMESTAMP NOT NULL,
  last_token_hash VARCHAR(255),
  previous_token_hash VARCHAR(255),
  token_subtype VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  token_updated_at TIMESTAMP NOT NULL,
  last_used_at TIMESTAMP,

  INDEX idx_user_id (user_id),
  INDEX idx_expiry (expiry_at),
  UNIQUE KEY uk_user_client (user_id, client_id)
);
```

Update `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ogiri_sample
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### H2 (In-Memory, for Development/Testing)

No setup required! H2 auto-creates on startup.

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:ogiri_sample
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop  # Auto-create schema on startup
```

### MongoDB (NoSQL Alternative)

Implement a custom `TokenRepository<Token>` for MongoDB:

```kotlin
@Repository
class MongoTokenRepository(private val mongoTemplate: MongoTemplate) : TokenRepository<Token> {
  override fun findByUserIdAndClient(userId: Long, client: String): Token? {
    val query = Query(Criteria.where("userId").`is`(userId).and("client").`is`(client))
    return mongoTemplate.findOne(query, Token::class.java)
  }

  override fun deleteExpiredTokens(expiryBefore: Instant) {
    val query = Query(Criteria.where("expiryAt").lt(expiryBefore))
    mongoTemplate.remove(query, Token::class.java)
  }
}
```

Configure in `application.yml`:
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/ogiri_sample
```

### Using Existing Token Table

If your project already has a token/session table, implement `TokenRepository<T>` as an adapter:

```kotlin
@Repository
class ExistingTokenAdapter(private val legacyTokenService: LegacyTokenService)
  : TokenRepository<LegacyToken> {

  override fun findByUserIdAndClient(userId: Long, client: String) =
    legacyTokenService.find(userId, client)

  override fun deleteExpiredTokens(expiryBefore: Instant) =
    legacyTokenService.deleteExpired(expiryBefore)
}
```

Then wire it in your configuration:
```kotlin
@Configuration
class TokenRepositoryConfig {
  @Bean
  fun tokenRepository(legacyTokenService: LegacyTokenService) =
    ExistingTokenAdapter(legacyTokenService)
}
```

## Configuration

Both samples use `application.yml` in `src/main/resources/`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ogiri_sample
    username: postgres
    password: postgres

ogiri:
  security:
    register-filter: true
  auth:
    batch-grace-seconds: 30
    rotate-on-write-only: false
    rotate-stale-seconds: 3600
```

Customize these values for your environment.

## Testing the Samples

### Health Check

```bash
curl http://localhost:8080/api/health
# Response: {"status":"UP"}
```

### Authenticated Endpoint

```bash
curl http://localhost:8080/api/me
# Response (unauthenticated):
# {"authenticated":false,"principal":"anonymous"}
```

To test with authentication, you'll need to:

1. Create a token via TokenService (see the sample code)
2. Send it in the `access-token` header:

```bash
curl -H "access-token: YOUR_TOKEN" http://localhost:8080/api/me
```

## Integration Guide

To use these samples as a basis for your own Spring Boot application:

1. **Copy the structure**: Use the sample directory layout as a template
2. **Implement TokenUserDirectory**: Replace the in-memory implementation with database lookups
3. **Implement TokenRepository**: Create a real JPA repository
4. **Configure RouteRegistry**: Add your actual application routes
5. **Add business logic**: Implement your domain-specific endpoints

## Common Tasks

### Adding a New Endpoint

Add a new method to `HealthController`:

```kotlin
@PostMapping("/api/protected")
fun protectedEndpoint(authentication: Authentication): ResponseEntity<Map<String, String>> {
  val user = authentication.principal as TokenUser
  return ResponseEntity.ok(mapOf("message" to "Hello, ${user.username}!"))
}
```

### Customizing User Loading

In `SampleTokenUserDirectory`, replace the in-memory map with database queries:

```kotlin
@Component
class SampleTokenUserDirectory(private val userService: UserService) : TokenUserDirectory {
  override fun loadUserByUsername(username: String) = userService.findByUsername(username)
  // ...
}
```

### Sub-Token Registration

Register custom sub-tokens in your configuration:

```kotlin
@Bean
fun deviceSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "device"
  override fun clientIdFor(parentClientId: String) = "$parentClientId.device"
  override fun expiry(parentExpiry: Instant) = Instant.now().plus(12, ChronoUnit.HOURS)
}
```

## Troubleshooting

### "Cannot find ogiri-core"

Ensure you've run `./gradlew build` from the root directory to build the core library.

### Database connection errors

Verify PostgreSQL is running and the database/user credentials match `application.yml`.

### Spotless formatting failures

Run `./gradlew spotlessApply` to auto-format code.

## Additional Resources

- **Core Library**: See `ogiri-core/` for the authentication library
- **Documentation**: `../../docs/AUTHENTICATION.md` for detailed authentication flow
- **README.md**: Project overview and setup

## License

Apache License 2.0 – See LICENSE file in the root directory.
