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
- **Gradle** (or use the provided Gradle wrapper)
- **H2** (in-memory, included by default) or **PostgreSQL** (optional)

## Configuration

### Default (In-Memory H2)

The application runs with H2 in-memory database by default. This requires zero setup and is ideal for development and testing.

### PostgreSQL Setup (Optional)

To use PostgreSQL instead:

1. Create a PostgreSQL database:

```bash
createdb ogiri_sample_java
```

2. Create `src/main/resources/application-postgres.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ogiri_sample_java
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

3. Run with the postgres profile:

```bash
./gradlew :sample:sample-java:bootRun --args='--spring.profiles.active=postgres'
```

### ogiri Security Configuration

The library is auto-configured in `com.quantipixels.ogiri.samples.java.config.SecurityConfig`. Key configuration properties:

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
./gradlew :sample:sample-java:bootRun
```

The application starts on `http://localhost:48080` with an in-memory H2 database. No database setup required.

### With PostgreSQL

First, follow the PostgreSQL setup steps above, then:

```bash
./gradlew :sample:sample-java:bootRun --args='--spring.profiles.active=postgres'
```

## API Endpoints

### Public Endpoints (No Authentication Required)

- **POST /api/auth/login** - Authenticate and obtain tokens

  ```bash
  curl -X POST http://localhost:48080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"user1","password":"password"}' \
    -v
  ```

  Response includes tokens in headers, cookies (if enabled), and body.

- **GET /api/health** - Application health check
  ```bash
  curl http://localhost:48080/api/health
  ```

### Secured Endpoints (Authentication Required)

The sample demonstrates **three authentication methods**. All methods are functionally equivalent:

#### Method 1: HTTP Headers

```bash
curl http://localhost:48080/api/demo/headers \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

#### Method 2: Secure Cookies

```bash
# Login with cookie storage
curl -X POST http://localhost:48080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -c cookies.txt

# Use stored cookies
curl http://localhost:48080/api/demo/cookies -b cookies.txt
```

#### Method 3: Bearer Token

```bash
# Extract Authorization header from login response
curl http://localhost:48080/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"
```

### Available Endpoints

| Endpoint            | Method | Auth | Description              |
| ------------------- | ------ | ---- | ------------------------ |
| `/api/health`       | GET    | No   | Health check             |
| `/api/me`           | GET    | Yes  | Current user info        |
| `/api/auth/login`   | POST   | No   | Login and get tokens     |
| `/api/auth/logout`  | POST   | Yes  | Logout and revoke tokens |
| `/api/demo/headers` | GET    | Yes  | Test header-based auth   |
| `/api/demo/cookies` | GET    | Yes  | Test cookie-based auth   |
| `/api/demo/bearer`  | GET    | Yes  | Test Bearer token auth   |
| `/api/demo/info`    | GET    | Yes  | General auth info        |

### Test Users

The sample includes two pre-configured users:

| Username | Password | Email             |
| -------- | -------- | ----------------- |
| user1    | password | user1@example.com |
| user2    | password | user2@example.com |

### Complete Testing Flow

```bash
# 1. Login and save response headers
curl -X POST http://localhost:48080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -v 2>&1 | grep -E "< (access-token|client|uid|expiry|Authorization):"

# 2. Extract tokens and test header auth
TOKEN="<access-token>"
CLIENT="<client>"
UID="<uid>"
EXPIRY="<expiry>"

curl http://localhost:48080/api/demo/headers \
  -H "access-token: $TOKEN" \
  -H "client: $CLIENT" \
  -H "uid: $UID" \
  -H "expiry: $EXPIRY"

# 3. Test general info endpoint
curl http://localhost:48080/api/demo/info \
  -H "access-token: $TOKEN" \
  -H "client: $CLIENT" \
  -H "uid: $UID" \
  -H "expiry: $EXPIRY"

# 4. Logout
curl -X POST http://localhost:48080/api/auth/logout \
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
- [Token Authentication Flow](../../docs/authentication.md)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security](https://spring.io/projects/spring-security)

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details
