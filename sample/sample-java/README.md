# Ògiri Security Java Sample Application

A complete example demonstrating how to integrate the **ogiri** token-based authentication library into a Spring Boot application using pure Java.

## Overview

This sample application showcases:
- Token-based authentication with JWT-like tokens
- Token rotation and batch grace windows
- Sub-token management (extensible for device-specific tokens, etc.)
- User authentication with Spring Security
- Route-based access control (public vs. authenticated endpoints)
- Database persistence with Spring Data JPA

## Prerequisites

- **Java 17+**
- **PostgreSQL** (or configure H2 for development)
- **Gradle** (or use the provided Gradle wrapper)

## Configuration

### Database Setup

1. Create a PostgreSQL database for the application:

```bash
createdb ogiri_sample
```

2. Update `src/main/resources/application.yml` with your database credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ogiri_sample
    username: postgres
    password: your_password
  jpa:
    hibernate:
      ddl-auto: validate
```

### ogiri Security Configuration

The library is auto-configured in `com.quantipixels.ogiri.samples.java.config.SecurityConfig`. Key configuration properties:

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

### From the Repository Root

Build and run the Java sample:

```bash
./gradlew :sample:sample-java:bootRun
```

### Standalone

From the `sample-java` directory:

```bash
gradle bootRun
```

The application starts on `http://localhost:8080`

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
- **SampleToken** - JPA entity extending `OgiriBaseToken`

### Repository
- **SampleTokenRepository** - Spring Data JPA + ogiri `OgiriTokenRepository` interface

### Security
- **SampleOgiriUserDirectory** - Implements `OgiriUserDirectory` for user lookup
- **SampleRouteRegistry** - Declares public routes via `OgiriRouteRegistry`
- **SecurityConfig** - Spring Security configuration

### Service
- **SampleTokenService** - Extends ogiri `OgiriTokenService` with custom token factory

## Development

### Testing

Run unit tests:

```bash
./gradlew :sample:sample-java:test
```

### Building

Build the application JAR:

```bash
./gradlew :sample:sample-java:build
```

The JAR will be available at `build/libs/sample-java-*.jar`

### Debugging

Enable debug logging by adding to `application.yml`:

```yaml
logging:
  level:
    com.quantipixels.ogiri: DEBUG
```

## Project Structure

```
sample-java/
├── src/main/java/com/quantipixels/ogiri/samples/java/
│   ├── Application.java                          # Entry point
│   ├── config/SecurityConfig.java               # Spring Security setup
│   ├── controller/HealthController.java         # REST endpoints
│   ├── entity/SampleToken.java                  # JPA token entity
│   ├── repository/SampleTokenRepository.java    # Data access
│   ├── security/                                # ogiri integration
│   │   ├── SampleRouteRegistry.java
│   │   ├── SampleOgiriUserDirectory.java
│   │   └── ...
│   └── service/SampleTokenService.java          # Token service
├── src/main/resources/
│   ├── application.yml                          # Configuration
│   └── db/migration/                            # Flyway migrations
├── build.gradle.kts                             # Gradle build config
└── README.md                                    # This file
```

## Extending the Sample

### Adding Custom Routes

Modify `SampleRouteRegistry` to declare additional public routes:

```java
public List<Route> routes() {
  return List.of(
    new Route(HttpMethod.GET, "/api/docs/**", true, false, null),
    new Route(HttpMethod.POST, "/api/custom", true, false, null)
  );
}
```

### Adding Sub-Tokens

Implement `OgiriSubTokenRegistration` to create domain-specific tokens (device, chat, etc.):

```java
@Bean
public OgiriSubTokenRegistration deviceToken() {
  return new OgiriSubTokenRegistration() {
    @Override
    public String getName() { return "device"; }

    @Override
    public String clientIdFor(String parentClient) {
      return parentClient + ".device";
    }

    @Override
    public Instant expiry(Instant parentExpiry) {
      return parentExpiry.minus(1, ChronoUnit.HOURS);
    }
  };
}
```

## Troubleshooting

### Database Connection Issues
- Ensure PostgreSQL is running on the configured host/port
- Verify database credentials in `application.yml`
- Check that the database exists and schema is initialized

### Authentication Failures
- Confirm token headers are sent with each request (access-token, client, uid, expiry)
- Verify token hasn't expired using the expiry header
- Check that user credentials match values in `SampleOgiriUserDirectory`

### Token Rotation Issues
- Token rotation only occurs outside the batch grace window
- By default, 5 second grace period allows requests within that window without rotation
- Adjust `batch-grace-seconds` to change this behavior

## References

- [ogiri Documentation](../../docs/)
- [Token Authentication Flow](../../docs/AUTHENTICATION.md)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security](https://spring.io/projects/spring-security)

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details
