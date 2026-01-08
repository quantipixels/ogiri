# Java Sample Application - Claude Code Instructions

## Overview

Sample Spring Boot application demonstrating ogiri-security library integration using pure Java. This sample shows how to implement token-based authentication with support for headers, cookies, and Bearer tokens.

**Language:** Java 17+
**Framework:** Spring Boot 3.5+
**Database:** PostgreSQL (H2 available for testing)
**Authentication:** Ogiri Security Library

## Quick Commands

```bash
# Start the application
./gradlew :sample:sample-java:bootRun

# Run tests
./gradlew :sample:sample-java:test

# Run tests with coverage
./gradlew :sample:sample-java:test jacocoTestReport

# Format code
./gradlew :sample:sample-java:spotlessApply
```

## Project Structure

```
src/main/java/com/quantipixels/ogiri/samples/java/
├── controller/
│   ├── HealthController.java    # Health check and basic auth info
│   ├── AuthController.java      # Login and logout endpoints
│   └── DemoController.java      # Authentication method demonstrations
├── security/
│   ├── SampleOgiriUserDirectory.java  # In-memory user directory
│   └── SampleRouteRegistry.java       # Public/protected route declarations
├── repository/
│   ├── SampleTokenRepository.java        # Spring Data JPA interface
│   └── SampleTokenRepositoryAdapter.java # Ogiri repository adapter
└── config/
    └── SecurityConfig.java               # Spring Security configuration
```

## Testing Authentication

### 1. Login to Get Tokens

```bash
curl -X POST http://localhost:48080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -v
```

The response includes:

- **Response Headers:** `access-token`, `client`, `uid`, `expiry`, `Authorization` (Bearer)
- **Response Cookies:** `access-token`, `client`, `uid`, `expiry` (if cookie config enabled)
- **Response Body:** JSON with token details

### 2. Test with HTTP Headers

```bash
curl http://localhost:48080/api/demo/headers \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

### 3. Test with Cookies

```bash
# Extract cookies from login response and use them
curl http://localhost:48080/api/demo/cookies \
  -b "access-token=<token>;client=<client>;uid=<uid>;expiry=<expiry>"

# Or use -c/-b for automatic cookie handling
curl -X POST http://localhost:48080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -c cookies.txt

curl http://localhost:48080/api/demo/cookies -b cookies.txt
```

### 4. Test with Bearer Token

```bash
# Extract the Authorization header from login response
curl http://localhost:48080/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"
```

### 5. Test General Auth Endpoint

```bash
# Works with any auth method
curl http://localhost:48080/api/demo/info \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

### 6. Logout

```bash
curl -X POST http://localhost:48080/api/auth/logout \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

## Available Endpoints

| Endpoint            | Method | Auth Required | Description              |
| ------------------- | ------ | ------------- | ------------------------ |
| `/api/health`       | GET    | No            | Health check             |
| `/api/me`           | GET    | Yes           | Current user info        |
| `/api/auth/login`   | POST   | No            | Login and get tokens     |
| `/api/auth/logout`  | POST   | Yes           | Logout and revoke tokens |
| `/api/demo/headers` | GET    | Yes           | Test header-based auth   |
| `/api/demo/cookies` | GET    | Yes           | Test cookie-based auth   |
| `/api/demo/bearer`  | GET    | Yes           | Test Bearer token auth   |
| `/api/demo/info`    | GET    | Yes           | General auth info        |

## Test Users

The sample includes two in-memory users:

| Username | Password | Email             |
| -------- | -------- | ----------------- |
| user1    | password | user1@example.com |
| user2    | password | user2@example.com |

## Configuration

See `src/main/resources/application.yml` for ogiri configuration:

```yaml
ogiri:
  auth:
    max-clients: 24 # Max tokens per user
    batch-grace-seconds: 30 # Batch request window
    token-lifespan-days: 14 # Token TTL
    rotate-stale-seconds: 3600 # Force rotation after 1 hour
  cookies:
    enabled: true # Enable secure cookies
    secure: false # false for localhost (use true in production)
    http-only: true # Prevent JavaScript access
    same-site: Lax # CSRF protection
```

## Integration Points

### Repository Adapter Pattern

The Java sample uses Spring Data JPA with an adapter pattern to implement `OgiriTokenRepository`:

```java
@Component
public class SampleTokenRepositoryAdapter implements OgiriTokenRepository<SampleToken> {
    private final SampleTokenRepository jpaRepository;

    // Delegate to Spring Data JPA repository
    @Override
    public SampleToken save(SampleToken token) {
        return jpaRepository.save(token);
    }
    // ... other methods
}
```

### User Directory

In-memory user directory implementation:

```java
@Component
public class SampleOgiriUserDirectory implements OgiriUserDirectory {
    @Override
    public OgiriUser loadUserByUsername(String username) { ... }

    @Override
    public OgiriUser findById(Long id) { ... }
    // ... other methods
}
```

### Route Registry

Declares public routes that don't require authentication:

```java
@Component
public class SampleRouteRegistry implements OgiriRouteRegistry {
    @Override
    public List<OgiriRoute> routes() {
        return List.of(
            new OgiriRoute(HttpMethod.POST, "/api/auth/login", ...),
            new OgiriRoute(HttpMethod.GET, "/api/health", ...)
        );
    }
}
```

## Development Tips

- **PostgreSQL Setup:** Requires PostgreSQL running on localhost:5432 (configurable via env vars)
- **H2 Testing:** For quick testing without PostgreSQL, modify application.yml to use H2 in-memory
- **Debug Logging:** Set `com.quantipixels.ogiri: DEBUG` to see authentication flow
- **Token Rotation:** Tokens rotate automatically based on `rotate-stale-seconds` setting
- **Batch Requests:** Multiple simultaneous requests use grace period to avoid token thrashing

## Java-Specific Patterns

### Records (Java 17+)

The sample uses Java records for immutable data transfer objects:

```java
public record LoginRequest(String username, String password) {}
public record AuthResponse(String accessToken, String client, String uid, String expiry, String message) {}
```

### Stream API

Leverages Java Streams for functional-style collection operations:

```java
List<String> authorities = authentication.getAuthorities().stream()
    .map(GrantedAuthority::getAuthority)
    .collect(Collectors.toList());
```

### Optional

Uses `Optional` for null-safe operations where appropriate.

## See Also

- Main library documentation: `../../CLAUDE.md`
- Kotlin sample: `../sample-kotlin/`
- API documentation: `../../docs/`
