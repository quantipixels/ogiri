# Ògiri Security Kotlin Sample Application

A complete example demonstrating how to integrate the **ogiri** token-based authentication library into a Spring Boot application using Kotlin.

## Overview

This sample application showcases:

- Token-based authentication with rotating tokens
- Token rotation and batch grace windows
- Sub-token management (extensible for device-specific tokens, etc.)
- User authentication with Spring Security
- Route-based access control (public vs. authenticated endpoints)
- Database persistence with Spring Data JPA
- Kotlin idioms and coroutine-friendly patterns

## Prerequisites

- **Java 17+**
- **Kotlin 2.0+**
- **Gradle** (or use the provided Gradle wrapper)
- **PostgreSQL** (optional; H2 in-memory is default for quick setup)

## Configuration

### Default (In-Memory H2)

The application runs with H2 in-memory database by default. This requires zero setup and is ideal for development and testing.

The H2 console is available at `http://localhost:48081/h2-console` (leave username as `sa`, password blank).

### PostgreSQL Setup (Optional)

To use PostgreSQL instead:

1. Create a PostgreSQL database:

```bash
createdb ogiri_sample_kotlin
```

2. Create `src/main/resources/application-postgres.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ogiri_sample_kotlin
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

3. Create the schema using the SQL from `../../docs/` or Flyway migrations

4. Run with the postgres profile:

```bash
./gradlew :sample:sample-kotlin:bootRun --args='--spring.profiles.active=postgres'
```

### ogiri Security Configuration

The library is auto-configured in `com.quantipixels.ogiri.samples.kotlin.config.SecurityConfig`. Key configuration properties:

```yaml
ogiri:
  auth:
    max-clients: 24 # Max concurrent clients per user
    batch-grace-seconds: 30 # Grace period for token batch requests
    token-lifespan-days: 14 # Token expiration in days
  security:
    register-filter: true # Auto-register authentication filter
```

## Running the Application

### Default (In-Memory H2)

From the repository root:

```bash
./gradlew :sample:sample-kotlin:bootRun
```

The application starts on `http://localhost:48081` with an in-memory H2 database. No database setup required.

### With PostgreSQL

First, follow the PostgreSQL setup steps above, then:

```bash
./gradlew :sample:sample-kotlin:bootRun --args='--spring.profiles.active=postgres'
```

## API Endpoints

### Public Endpoints (No Authentication Required)

- **POST /api/auth/login** - Authenticate and obtain tokens

  ```bash
  curl -X POST http://localhost:48081/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"user1","password":"password"}' \
    -v
  ```

  Response includes tokens in headers, cookies (if enabled), and body.

- **GET /api/health** - Application health check
  ```bash
  curl http://localhost:48081/api/health
  ```

### Secured Endpoints (Authentication Required)

The sample demonstrates **three authentication methods**. All methods are functionally equivalent:

#### Method 1: HTTP Headers

```bash
curl http://localhost:48081/api/demo/headers \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

#### Method 2: Secure Cookies

```bash
# Login with cookie storage
curl -X POST http://localhost:48081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -c cookies.txt

# Use stored cookies
curl http://localhost:48081/api/demo/cookies -b cookies.txt
```

#### Method 3: Bearer Token

```bash
# Extract Authorization header from login response
curl http://localhost:48081/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"
```

### Available Endpoints

| Endpoint                 | Method | Auth | Description                          |
| ------------------------ | ------ | ---- | ------------------------------------ |
| `/api/health`            | GET    | No   | Health check                         |
| `/api/me`                | GET    | Yes  | Current user info                    |
| `/api/auth/login`        | POST   | No   | Login and get tokens                 |
| `/api/auth/logout`       | POST   | Yes  | Logout and revoke tokens             |
| `/api/demo/headers`      | GET    | Yes  | Test header-based auth               |
| `/api/demo/cookies`      | GET    | Yes  | Test cookie-based auth               |
| `/api/demo/bearer`       | GET    | Yes  | Test Bearer token auth               |
| `/api/demo/info`         | GET    | Yes  | General auth info                    |
| `/api/test/expire-token` | POST   | Yes  | Backdate token expiry (dev/test use) |

### Test Users

The sample includes two pre-configured users:

| Username | Password | Email             |
| -------- | -------- | ----------------- |
| user1    | password | user1@example.com |
| user2    | password | user2@example.com |

### Complete Testing Flow

```bash
# 1. Login and save response headers
curl -X POST http://localhost:48081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -v 2>&1 | grep -E "< (access-token|client|uid|expiry|Authorization):"

# 2. Extract tokens and test header auth
TOKEN="<access-token>"
CLIENT="<client>"
UID="<uid>"
EXPIRY="<expiry>"

curl http://localhost:48081/api/demo/headers \
  -H "access-token: $TOKEN" \
  -H "client: $CLIENT" \
  -H "uid: $UID" \
  -H "expiry: $EXPIRY"

# 3. Test general info endpoint
curl http://localhost:48081/api/demo/info \
  -H "access-token: $TOKEN" \
  -H "client: $CLIENT" \
  -H "uid: $UID" \
  -H "expiry: $EXPIRY"

# 4. Logout
curl -X POST http://localhost:48081/api/auth/logout \
  -H "access-token: $TOKEN" \
  -H "client: $CLIENT" \
  -H "uid: $UID" \
  -H "expiry: $EXPIRY"
```

## Key Components

### Entity

- **SampleToken** - JPA entity extending `OgiriBaseToken`

### Repository

- **SampleTokenRepository** - Spring Data JPA + ogiri `OgiriTokenRepository` interface

### Security

- **SampleOgiriUserDirectory** - Implements `OgiriUserDirectory` for user lookup
- **SampleRouteRegistry** - Declares public routes via `OgiriRouteRegistry`
- **SecurityConfig** - Spring Security configuration

### Service

- **SampleTokenService** - Extends ogiri `OgiriTokenService` with custom token factory

### Test Utilities

- **TestController** - Exposes `POST /api/test/expire-token` to backdate the current session's expiry, enabling the full expiry → 401 → redirect flow without waiting for a real TTL (`@Profile("!jdbc")`)

## Development

### Testing

Run unit tests:

```bash
./gradlew :sample:sample-kotlin:test
```

### Building

Build the application JAR:

```bash
./gradlew :sample:sample-kotlin:build
```

The JAR will be available at `build/libs/sample-kotlin-*.jar`

### Debugging

Enable debug logging by adding to `application.yml`:

```yaml
logging:
  level:
    com.quantipixels.ogiri: DEBUG
```

## Project Structure

```text
sample-kotlin/
├── src/main/kotlin/com/quantipixels/ogiri/samples/kotlin/
│   ├── Application.kt                           # Entry point
│   ├── config/SecurityConfig.kt                 # Spring Security setup
│   ├── controller/HealthController.kt           # REST endpoints
│   ├── entity/SampleToken.kt                    # JPA token entity
│   ├── repository/SampleTokenRepository.kt      # Data access
│   ├── security/                                # ogiri integration
│   │   ├── SampleRouteRegistry.kt
│   │   ├── SampleOgiriUserDirectory.kt
│   │   └── ...
│   └── service/SampleTokenService.kt            # Token service
├── src/main/resources/
│   ├── application.yml                          # Configuration
│   └── db/migration/                            # Flyway migrations
├── build.gradle.kts                             # Gradle build config
└── README.md                                    # This file
```

## Kotlin-Specific Patterns

### Data Classes

The sample uses Kotlin data classes for token entities, providing automatic `equals()`, `hashCode()`, and `toString()`:

```kotlin
data class SampleToken(...) : OgiriBaseToken()
```

### Extension Functions

Leverage Kotlin extension functions for cleaner API usage:

```kotlin
// Example extension for token filtering
val appTokens = tokens.filterByTokenType(TokenType.APP)
```

### Scope Functions

Use `apply`, `let`, and `also` for fluent configuration:

```kotlin
SampleToken(...).apply {
  plainToken = generatedToken
}
```

## Extending the Sample

### Adding Custom Routes

Modify `SampleRouteRegistry` to declare additional public routes:

```kotlin
override fun routes() = listOf(
  Route(HttpMethod.GET, "/api/docs/**", rateLimit = true, useAuth = false),
  Route(HttpMethod.POST, "/api/custom", rateLimit = true, useAuth = false)
)
```

### Adding Sub-Tokens

Implement `OgiriSubTokenRegistration` to create domain-specific tokens:

```kotlin
@Bean
fun deviceToken(): OgiriSubTokenRegistration = object : OgiriSubTokenRegistration {
  override val name = "device"
  override val includeByDefault = true

  override fun clientIdFor(parentClientId: String) = "$parentClientId.device"

  override fun expiry(parentExpiry: Instant) =
    parentExpiry.minus(1, ChronoUnit.HOURS)
}
```

## Troubleshooting

### Using H2 In-Memory Database

- Data is lost when the application stops (normal for in-memory)
- To persist data, switch to PostgreSQL following the setup steps above
- Access the H2 console at `http://localhost:48081/h2-console` to browse the schema

### Database Connection Issues (PostgreSQL)

- Ensure PostgreSQL is running on the configured host/port
- Verify database credentials in `application-postgres.yml`
- Check that the database exists and schema is initialized

### Authentication Failures

- Confirm token headers are sent with each request (access-token, client, uid, expiry)
- Verify token hasn't expired using the expiry header
- Check that user credentials match values in `SampleOgiriUserDirectory`

### Token Rotation Issues

- Token rotation only occurs outside the batch grace window
- By default, 30 second grace period allows requests within that window without rotation
- Adjust `batch-grace-seconds` to change this behavior

## References

- [ogiri Documentation](../../docs/)
- [Token Authentication Flow](../../docs/authentication.md)
- [Spring Boot with Kotlin](https://spring.io/guides/tutorials/spring-boot-kotlin/)
- [Kotlin Language Documentation](https://kotlinlang.org/docs/home.html)
- [Spring Security](https://spring.io/projects/spring-security)

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details
