# Java Sample Application - Claude Code Instructions

## Overview

Sample Spring Boot application demonstrating ogiri-security library integration using pure Java. This sample shows how to implement token-based authentication with support for headers, cookies, and Bearer tokens.

**Language:** Java 17+
**Framework:** Spring Boot 3.5+
**Database:** H2 (in-memory)
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

See `src/main/java/com/quantipixels/ogiri/samples/java/` for project layout.

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

See `src/main/resources/application.yml` for full ogiri configuration.

## Integration Points

### Direct Repository Pattern

Since ogiri 1.3.1, `OgiriTokenRepository` method names follow Spring Data conventions.
`SampleTokenRepository` extends both interfaces directly — no adapter needed:

See `repository/SampleTokenRepository.java`

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

- **H2 Console:** Access at http://localhost:48080/h2-console (credentials in application.yml)
- **Debug Logging:** Set `com.quantipixels.ogiri: DEBUG` to see authentication flow
- **Token Rotation:** Tokens rotate automatically based on `rotate-stale-seconds` setting
- **Batch Requests:** Multiple simultaneous requests use grace period to avoid token thrashing

## See Also

- Main library documentation: `../../CLAUDE.md`
- Client library: `../../ogiri-client/`
- Kotlin sample: `../sample-kotlin/`
- React sample: `../sample-react/`
- API documentation: `../../docs/`
