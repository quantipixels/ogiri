# Sample Applications

Minimal Spring Boot applications demonstrating ogiri integration.

## Available Samples

| Sample | Language | Path |
|--------|----------|------|
| Java | Pure Java | `sample-java/` |
| Kotlin | Kotlin | `sample-kotlin/` |

Both samples implement the same functionality:
- `OgiriUserDirectory` - In-memory user directory
- `RouteRegistry` - Public route declarations
- `TokenRepository` - JPA token persistence
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

# Authenticated endpoint (will fail without token)
curl http://localhost:8080/api/me
```

## Project Structure

```
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

## Configuration

Both samples use this configuration:

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

## Using as Template

1. Copy sample directory structure
2. Replace in-memory `OgiriUserDirectory` with database lookups
3. Configure your database connection
4. Add your business logic endpoints

See [Quickstart Guide](../docs/quickstart.md) for integration details.
