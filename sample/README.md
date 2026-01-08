# Sample Applications

Minimal Spring Boot applications demonstrating ogiri integration.

## Available Samples

| Sample | Language  | Path             |
| ------ | --------- | ---------------- |
| Java   | Pure Java | `sample-java/`   |
| Kotlin | Kotlin    | `sample-kotlin/` |

Both samples implement the same functionality:

- `OgiriUserDirectory` - In-memory user directory
- `OgiriRouteRegistry` - Public route declarations
- `OgiriTokenRepository` - JPA token persistence
- Login/logout endpoints
- Demo endpoints for testing all three auth methods (headers, cookies, Bearer token)
- Health and authenticated endpoints

## Prerequisites

- Java 17+
- PostgreSQL on `localhost:5432`

## Quick Start

### 1. Start PostgreSQL

```bash
# Docker
docker run --name ogiri-db -e POSTGRES_PASSWORD=postgres -d -p 5432:5432 postgres:15
docker exec ogiri-db createdb -U postgres ogiri_sample

# Or native
createdb ogiri_sample
```

### 2. Import Schema

```bash
psql -U postgres -d ogiri_sample < ../ogiri-core/src/main/resources/ogiri/db/ogiri-user-tokens.sql
```

### 3. Run Sample

```bash
# Kotlin
./gradlew :sample:sample-kotlin:bootRun

# Java
./gradlew :sample:sample-java:bootRun
```

### 4. Test Endpoints

```bash
# Health check (public)
curl http://localhost:8080/api/health

# Login to get tokens
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -v

# Test with headers (extract tokens from login response)
curl http://localhost:8080/api/demo/headers \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"

# Test with cookies
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1","password":"password"}' \
  -c cookies.txt

curl http://localhost:8080/api/demo/cookies -b cookies.txt

# Test with Bearer token (extract Authorization header from login)
curl http://localhost:8080/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"

# Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

## Project Structure

```text
sample-kotlin/
├── src/main/kotlin/
│   └── com/quantipixels/ogiri/samples/kotlin/
│       ├── Application.kt
│       ├── config/SecurityConfig.kt
│       ├── security/
│       │   ├── SampleOgiriUserDirectory.kt
│       │   └── SampleRouteRegistry.kt
│       ├── repository/SampleTokenRepository.kt
│       └── controller/HealthController.kt
└── src/main/resources/application.yml
```

## Authentication Methods

Both samples demonstrate **three authentication methods**:

1. **HTTP Headers** - Send auth tokens in request headers
2. **Secure Cookies** - Use HTTPOnly cookies for web browsers
3. **Bearer Token** - Standard OAuth2-style Authorization header

All three methods work identically from the server's perspective. The library extracts authentication from headers first, falling back to cookies, then to Bearer tokens.

## Configuration

Kotlin sample (H2 in-memory):

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    batch-grace-seconds: 30
    rotate-on-write-only: false
    rotate-stale-seconds: 3600
  cookies:
    enabled: true
    secure: false # false for localhost testing
    http-only: true
    same-site: Lax
```

Java sample (PostgreSQL):

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    max-clients: 24
    batch-grace-seconds: 30
    token-lifespan-days: 14
    rotate-stale-seconds: 3600
  cookies:
    enabled: true
    secure: false
    http-only: true
    same-site: Lax
```

## Using as Template

1. Copy sample directory structure
2. Replace in-memory `OgiriUserDirectory` with database lookups
3. Configure your database connection
4. Add your business logic endpoints

See [Quickstart Guide](../docs/quickstart.md) for integration details.
