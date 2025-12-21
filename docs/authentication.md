# Authentication Flow

How ogiri authenticates requests, rotates tokens, and manages headers.

## Request Lifecycle

```
Request → Filter → Bypass Check → Header Extraction → Token Validation → Rotation → Response
```

### 1. Filter Entry

`OgiriTokenAuthenticationFilter.doFilterInternal()` intercepts every request.

### 2. Bypass Check

`AuthenticationBypassDecider.canSkip()` returns `true` for:

- Already authenticated users (SecurityContext populated)
- Public routes declared in `OgiriRouteRegistry`
- CORS preflight requests (OPTIONS method)
- Health and docs paths (`/health`, `/actuator/**`, `/swagger-ui/**`)

### 3. Header Extraction

`AuthHeader.extractAuthHeader()` parses authentication from:

**Individual headers (preferred):**

```
access-token: <token-hash>
client: web
uid: 123
expiry: 2025-12-25T00:00:00Z
```

**Bearer token (fallback):**

```
Authorization: Bearer eyJhY2Nlc3MtdG9rZW4iOiJ4eXoiLCJjbGllbnQiOiJ3ZWIiLCJ1aWQiOiIxMjMiLCJleHBpcnkiOiIyMDI1LTEyLTI1In0=
```

The Bearer token decodes to:

```json
{
  "access-token": "xyz",
  "client": "web",
  "uid": "123",
  "expiry": "2025-12-25"
}
```

### 4. Token Validation

`OgiriTokenService.validToken()` verifies:

1. Token hash matches database record
2. Token is not expired
3. Grace period tokens (`lastToken`, `previousToken`) are accepted during rotation

### 5. Token Rotation

Based on configuration:

| Condition                            | Action                                   |
| ------------------------------------ | ---------------------------------------- |
| Within batch grace window            | Update `lastUsedAt` only, no new headers |
| Outside batch window                 | Rotate token, emit new headers           |
| `rotate-on-write-only=true`          | Only rotate on POST/PUT/DELETE           |
| Token exceeds `rotate-stale-seconds` | Force rotation                           |

### 6. Response

On success:

- `SecurityContext` populated with authenticated user
- New auth headers appended (if rotated)

On failure:

- `SecurityContext` cleared
- `AuthenticationEntryPoint` returns error response

## Token Rotation

### Batch Window

Prevents token thrashing from rapid requests:

```yaml
ogiri:
  auth:
    batch-grace-seconds: 5 # Requests within 5s share same token
```

Within the window, only `lastUsedAt` is updated.

### Staleness Rotation

Force rotation after a time period:

```yaml
ogiri:
  auth:
    rotate-stale-seconds: 3600 # Rotate tokens older than 1 hour
```

### Write-Only Rotation

Only rotate on mutating requests:

```yaml
ogiri:
  auth:
    rotate-on-write-only: true # GET requests don't rotate
```

## Headers

### Request Headers

Clients send these on authenticated requests:

| Header         | Description                               |
| -------------- | ----------------------------------------- |
| `access-token` | Token hash                                |
| `client`       | Client identifier (e.g., "web", "mobile") |
| `uid`          | User identifier                           |
| `expiry`       | Token expiration (ISO-8601)               |

Or use a single Bearer header containing Base64-encoded JSON.

### Response Headers

After login or rotation:

| Header         | Description                             |
| -------------- | --------------------------------------- |
| `access-token` | New token hash                          |
| `client`       | Client identifier                       |
| `uid`          | User identifier                         |
| `expiry`       | New expiration                          |
| `sub-tokens`   | Base64-encoded sub-token map (optional) |

### Sub-Token Header

When sub-tokens are issued:

```
sub-tokens: eyJkZXZp******************MFoifX0=
```

Decodes to:

```json
{
  "device": {
    "client": "app.device",
    "token": "abc123",
    "expiry": "2025-12-25T00:00:00Z"
  }
}
```

## Route Registry

Declare unauthenticated routes:

```kotlin
@Component
class MyRouteRegistry : OgiriRouteRegistry {
  override fun routes() = listOf(
    OgiriRoute.get("/public/**"),
    OgiriRoute.post("/api/auth/login"),
    OgiriRoute.post("/api/auth/register"),
    OgiriRoute.get("/health"),
    OgiriRoute.get("/api/docs/**")
  )
}
```

Routes support wildcards:

- `*` matches single path segment
- `**` matches multiple path segments

## Error Handling

Use `SecurityServiceException` for auth errors:

```kotlin
throw SecurityServiceException("error.auth.invalid_token", "Token is invalid")
```

Recommended error codes:

- `error.auth.invalid_token`
- `error.auth.expired_token`
- `error.auth.missing_headers`
- `error.auth.user_not_found`

Handle in `@ControllerAdvice`:

```kotlin
@ExceptionHandler(SecurityServiceException::class)
fun handleAuthError(ex: SecurityServiceException): ResponseEntity<*> {
  return ResponseEntity
    .status(HttpStatus.UNAUTHORIZED)
    .body(mapOf("error" to ex.code, "message" to ex.message))
}
```

## Security Best Practices

1. **Never log raw tokens** - Use `SecurityHelpers` for parsing
2. **Register public routes** - Prevent accidental lockouts
3. **Use SecurityServiceException** - Avoid leaking internal errors
4. **Validate identifiers** - Use `IdentifierPolicy` before database queries

## Testing

Use in-memory fixtures for testing:

```kotlin
@Test
fun `should authenticate valid token`() {
  val token = tokenService.createNewAuthToken(userId, "test-client")

  mockMvc.get("/api/protected") {
    header("access-token", token.accessToken)
    header("client", token.client)
    header("uid", userId.toString())
    header("expiry", token.expiry.toString())
  }.andExpect {
    status { isOk() }
  }
}
```

See `OgiriTokenAuthenticationFilterTest` for comprehensive examples.
