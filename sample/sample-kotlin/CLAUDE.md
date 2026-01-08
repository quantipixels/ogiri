# Kotlin Sample Application - Claude Code Instructions

## Overview

Sample Spring Boot application demonstrating ogiri-security library integration. This sample shows how to implement token-based authentication with support for headers, cookies, and Bearer tokens.

**Language:** Kotlin
**Framework:** Spring Boot 3.5+
**Database:** H2 (in-memory)
**Authentication:** Ogiri Security Library

## Quick Commands

```bash
# Start the application
./gradlew :sample:sample-kotlin:bootRun

# Run tests
./gradlew :sample:sample-kotlin:test

# Run tests with coverage
./gradlew :sample:sample-kotlin:test jacocoTestReport

# Format code
./gradlew :sample:sample-kotlin:spotlessApply
```

## Project Structure

```
src/main/kotlin/com/quantipixels/ogiri/samples/kotlin/
├── controller/
│   ├── HealthController.kt    # Health check and basic auth info
│   ├── AuthController.kt      # Login and logout endpoints
│   └── DemoController.kt      # Authentication method demonstrations
├── security/
│   ├── SampleOgiriUserDirectory.kt  # In-memory user directory
│   └── SampleRouteRegistry.kt       # Public/protected route declarations
├── service/
│   └── SampleTokenService.kt        # Token service implementation
├── entity/
│   ├── SampleToken.kt               # JPA token entity
│   └── DirectToken.kt               # Direct token implementation
└── repository/
    └── SampleTokenRepository.kt     # Combined JPA + OgiriTokenRepository interface
```

## Testing Authentication

### 1. Login to Get Tokens

```bash
curl -X POST http://localhost:48081/api/auth/login \
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
curl http://localhost:48081/api/demo/headers \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

### 3. Test with Cookies

```bash
# Extract cookies from login response and use them
curl http://localhost:48081/api/demo/cookies \
  -b "access-token=<token>;client=<client>;uid=<uid>;expiry=<expiry>"

# Or use -c/-b for automatic cookie handling
curl -X POST http://localhost:48081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -c cookies.txt

curl http://localhost:48081/api/demo/cookies -b cookies.txt
```

### 4. Test with Bearer Token

```bash
# Extract the Authorization header from login response
curl http://localhost:48081/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"
```

### 5. Test General Auth Endpoint

```bash
# Works with any auth method
curl http://localhost:48081/api/demo/info \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

### 6. Logout

```bash
curl -X POST http://localhost:48081/api/auth/logout \
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
    batch-grace-seconds: 30 # Batch request window
    rotate-stale-seconds: 3600 # Force rotation after 1 hour
  cookies:
    enabled: true # Enable secure cookies
    secure: false # false for localhost (use true in production)
    http-only: true # Prevent JavaScript access
    same-site: Lax # CSRF protection
```

## Integration Points

### Custom Token Service

The sample extends `OgiriTokenService` to work with JPA entities:

```kotlin
@Service
@Primary
class SampleTokenService(
    sampleTokenRepository: SampleTokenRepository,
    passwordEncoder: PasswordEncoder,
    // ... other dependencies
) : OgiriTokenService<SampleToken>(...) {
    override fun tokenFactory(...): SampleToken {
        // Create SampleToken JPA entity
    }
}
```

### User Directory

In-memory user directory implementation:

```kotlin
@Component
class SampleOgiriUserDirectory : OgiriUserDirectory {
    override fun loadUserByUsername(username: String): OgiriUser
    override fun findById(id: Long): OgiriUser?
    // ... other methods
}
```

### Route Registry

Declares public routes that don't require authentication:

```kotlin
@Component
class SampleRouteRegistry : OgiriRouteRegistry {
    override fun routes() = listOf(
        OgiriRoute(HttpMethod.POST, "/api/auth/login", ...),
        OgiriRoute(HttpMethod.GET, "/api/health", ...),
    )
}
```

## Development Tips

- **H2 Console:** Access at http://localhost:48081/h2-console (credentials in application.yml)
- **Debug Logging:** Set `com.quantipixels.ogiri: DEBUG` to see authentication flow
- **Token Rotation:** Tokens rotate automatically based on `rotate-stale-seconds` setting
- **Batch Requests:** Multiple simultaneous requests use grace period to avoid token thrashing

## See Also

- Main library documentation: `../../CLAUDE.md`
- Java sample: `../sample-java/`
- API documentation: `../../docs/`
