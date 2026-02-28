# Sample Applications

Minimal applications demonstrating Ogiri token auth integration.

## Available Samples

| Sample | Language   | Path             | Port  |
| ------ | ---------- | ---------------- | ----- |
| Java   | Pure Java  | `sample-java/`   | 48080 |
| Kotlin | Kotlin     | `sample-kotlin/` | 48081 |
| React  | TypeScript | `sample-react/`  | 5173  |

Both server samples implement the same functionality:

- `OgiriUserDirectory` — in-memory user directory
- `OgiriRouteRegistry` — public route declarations
- `OgiriTokenRepository` — JPA token persistence (H2 in-memory)
- Login / logout endpoints
- Demo endpoints for all three auth methods (headers, cookies, Bearer token)

## Prerequisites

- Java 17+

No external database required — both server samples use H2 in-memory.

## Quick Start

### Spring Boot servers

```bash
# Kotlin (port 48081)
./gradlew :sample:sample-kotlin:bootRun

# Java (port 48080)
./gradlew :sample:sample-java:bootRun
```

### React sample (standalone with mock)

```bash
cd sample-react
pnpm install
pnpm dev        # http://localhost:5173
```

### React + Kotlin (full stack)

```bash
cd sample-react
./run-live.sh kotlin --ui
# → starts Spring Boot on :48081
# → starts Vite on :5173, /api/* proxied to :48081
# → open http://localhost:5173
```

### React + Java (full stack)

```bash
cd sample-react
./run-live.sh java --ui
# → starts Spring Boot on :48080
# → starts Vite on :5173, /api/* proxied to :48080
# → open http://localhost:5173
```

### Integration tests only (no UI)

```bash
cd sample-react
./run-live.sh kotlin   # or java
```

## Default Credentials

Login accepts **email** (not username):

| Email               | Password   |
| ------------------- | ---------- |
| `user1@example.com` | `password` |
| `user2@example.com` | `password` |

## Test Endpoints

```bash
# Health check (public)
curl http://localhost:48081/api/health

# Login
curl -X POST http://localhost:48081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1@example.com","password":"password"}' \
  -v

# Authenticated request (extract tokens from login response headers)
curl http://localhost:48081/api/demo/info \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"

# Cookie-based auth
curl -X POST http://localhost:48081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1@example.com","password":"password"}' \
  -c cookies.txt

curl http://localhost:48081/api/demo/cookies -b cookies.txt

# Bearer token auth (Authorization header from login response)
curl http://localhost:48081/api/demo/bearer \
  -H "Authorization: Bearer <base64-encoded-json>"

# Logout
curl -X POST http://localhost:48081/api/auth/logout \
  -H "access-token: <token>" \
  -H "client: <client>" \
  -H "uid: <uid>" \
  -H "expiry: <expiry>"
```

Replace `48081` with `48080` for the Java sample.

## Project Structure

```text
sample-kotlin/
├── src/main/kotlin/.../
│   ├── Application.kt
│   ├── config/SecurityConfig.kt
│   ├── security/
│   │   ├── SampleOgiriUserDirectory.kt
│   │   └── SampleRouteRegistry.kt
│   ├── repository/SampleTokenRepository.kt
│   └── controller/
│       ├── AuthController.kt
│       ├── DemoController.kt
│       └── HealthController.kt
└── src/main/resources/application.yml

sample-react/
├── src/
│   ├── lib/
│   │   ├── auth.ts              # OgiriAuth — copy into your project
│   │   └── axios-ogiri.ts       # axios interceptors — copy alongside auth.ts
│   ├── api/client.ts            # axios instance wired to OgiriAuth
│   ├── auth/AuthProvider.tsx    # React context + useSyncExternalStore
│   └── mocks/                   # MSW handlers replicating the Ogiri protocol
└── run-live.sh                  # Start server → run tests or UI → teardown
```

## Authentication Methods

All three methods are functionally equivalent. The filter extracts auth from headers first, then cookies, then Bearer tokens.

1. **HTTP Headers** — `access-token`, `client`, `uid`, `expiry` request headers
2. **Secure Cookies** — HTTPOnly cookies, set automatically by the server on login
3. **Bearer Token** — `Authorization: Bearer <base64-json>` header

## Server Configuration

Both samples use H2 in-memory with these Ogiri defaults:

```yaml
ogiri:
  security:
    register-filter: true
  auth:
    batch-grace-seconds: 30
    rotate-stale-seconds: 3600
  cookies:
    enabled: true
    secure: false # false for localhost; set true in production (HTTPS)
    http-only: true
    same-site: Lax
```

## Using as Template

1. Copy the sample directory structure
2. Replace `SampleOgiriUserDirectory` with database lookups
3. Configure your database connection
4. Add your business logic endpoints

See [Quickstart Guide](../docs/quickstart.md) for integration details.
