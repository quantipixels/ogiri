# Ogiri Security Kotlin Sample Application

A complete example demonstrating how to integrate the **ogiri** token-based authentication library into a Spring Boot application using Kotlin.

## Overview

This sample application showcases:
- Token-based authentication with JWT-like tokens
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

The H2 console is available at `http://localhost:8080/h2-console` (leave username as `sa`, password blank).

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
    max-clients: 24              # Max concurrent clients per user
    batch-grace-seconds: 5       # Grace period for token batch requests
    token-lifespan-days: 14      # Token expiration in days
  security:
    register-filter: true        # Auto-register authentication filter
```

## Running the Application

### Default (In-Memory H2)

From the repository root:

```bash
./gradlew :sample:sample-kotlin:bootRun
```

The application starts on `http://localhost:8080` with an in-memory H2 database. No database setup required.

### With PostgreSQL

First, follow the PostgreSQL setup steps above, then:

```bash
./gradlew :sample:sample-kotlin:bootRun --args='--spring.profiles.active=postgres'
```

## API Endpoints

### Public Endpoints (No Authentication Required)

- **POST /api/auth/login** - Authenticate and obtain tokens
  ```bash
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"user1","password":"password"}'
  ```

- **GET /api/health** - Application health check
  ```bash
  curl http://localhost:8080/api/health
  ```

### Secured Endpoints (Authentication Required)

- **GET /api/secure** - Protected route
  ```bash
  curl -H "Authorization: Bearer <token>" http://localhost:8080/api/secure
  ```

Include these headers with authenticated requests:
- `access-token`: The access token
- `client`: The client identifier
- `uid`: The user ID
- `expiry`: Token expiration time

## Key Components

### Entity
- **SampleToken** - JPA entity extending `BaseToken`

### Repository
- **SampleTokenRepository** - Spring Data JPA + ogiri `TokenRepository` interface

### Security
- **SampleOgiriUserDirectory** - Implements `OgiriUserDirectory` for user lookup
- **SampleRouteRegistry** - Declares public routes via `RouteRegistry`
- **SecurityConfig** - Spring Security configuration

### Service
- **SampleTokenService** - Extends ogiri `TokenService` with custom token factory

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

```
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
data class SampleToken(...) : BaseToken()
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

Implement `SubTokenRegistration` to create domain-specific tokens:

```kotlin
@Bean
fun deviceToken(): SubTokenRegistration = object : SubTokenRegistration {
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
- Access the H2 console at `http://localhost:8080/h2-console` to browse the schema

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
- [Token Authentication Flow](../../docs/AUTHENTICATION.md)
- [Spring Boot with Kotlin](https://spring.io/guides/tutorials/spring-boot-kotlin/)
- [Kotlin Language Documentation](https://kotlinlang.org/docs/home.html)
- [Spring Security](https://spring.io/projects/spring-security)

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details
