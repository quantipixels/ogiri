# Authentication Flow

This page summarizes how `ogiri` authenticates requests and can be used to generate web documentation.

## Request lifecycle
- `OgiriTokenAuthenticationFilter` runs once per request and skips authentication when `AuthenticationBypassDecider.canSkip` returns true (already authenticated, whitelisted paths, CORS preflight, or public routes in `RouteCatalog`).
- If not skipped, the filter extracts headers via `AuthHeader.extractAuthHeader`, validates identifiers, and ensures an APP token type.
- `TokenService.validToken` verifies the hashed token (supports previous/last tokens for grace periods).
- Depending on the batch grace window, the filter either:
  - Calls `extendBatchBuffer` to record activity without rotating tokens, or
  - Calls `createNewAuthToken` to rotate and emit refreshed headers.
- Successful auth populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken` and appends refreshed headers (when rotation occurs).
- Authentication failures clear the context and delegate to the configured `AuthenticationEntryPoint`.

## Token rotation and batch window
- `TokenService.isBatchRequest` detects requests within `ogiri.auth.batch-grace-seconds`; these calls only extend `lastUsedAt` and do **not** emit new headers.
- Outside the grace window, `rotateTokensIfNeeded` may issue new APP + sub-tokens and append headers.
- `ogiri.auth.rotate-on-write-only=true` restricts rotation to mutating methods; `ogiri.auth.rotate-stale-seconds` enforces rotation when tokens exceed the staleness threshold.

## Headers
- Core headers: `access-token`, `client`, `uid`, `access-token-kind`, `expiry`, and `Authorization: Bearer <Base64 JSON>`.
- Sub-tokens (optional): `sub-tokens` header encodes a JSON map `{name: {client, token, expiry}}`.
- `AuthHeader.appendAuthHeaders` only writes non-blank values to avoid leaking partial tokens.

## Routes and bypass rules
- Public routes are described via `RouteRegistry.routes()` and aggregated by `RouteCatalog`.
- `SecurityHelpers` whitelists health checks and docs paths; preflight requests (`OPTIONS`) are also skipped.
- Keep unauthenticated endpoints registered in `RouteCatalog` to avoid accidental lockouts.

**Example - Custom Route Registry:**

*Kotlin*
```kotlin
@Component
class MyRouteRegistry : RouteRegistry {
  override fun registrations() = listOf(
    Route.get("/public/**"),
    Route.post("/api/auth/login"),
    Route.post("/api/auth/register"),
    Route.get("/health"),
    Route.get("/api/docs/**")
  )
}
```

*Java*
```java
@Component
public class MyRouteRegistry implements RouteRegistry {
  @Override
  public List<Route> registrations() {
    return List.of(
      Route.get("/public/**"),
      Route.post("/api/auth/login"),
      Route.post("/api/auth/register"),
      Route.get("/health"),
      Route.get("/api/docs/**")
    );
  }
}
```

## Sub-tokens
- Register `SubTokenRegistration` beans to mint protocol-specific tokens alongside APP tokens.
- `TokenService.issueSubTokens` respects `includeByDefault`; `renewSubToken` forces rotation for a specific sub-token and appends updated headers.
- Sub-token bearer decoding uses Base64 JSON payloads (`client`, `token`, `expiry`); server-side expiry is always enforced.

**Example - Custom Sub-token Registration:**

*Kotlin*
```kotlin
@Configuration
class SubTokenConfig {
  @Bean
  fun deviceSubToken(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "device"
    override val includeByDefault = false  // Only on explicit request
    override fun clientIdFor(parent: String) = "$parent.device"
    override fun expiry(parentExpiry: Instant) = parentExpiry.plus(30, ChronoUnit.DAYS)
  }

  @Bean
  fun apiSubToken(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "api"
    override val includeByDefault = true  // Always included
    override fun clientIdFor(parent: String) = "$parent.api"
    override fun expiry(parentExpiry: Instant) = parentExpiry  // Same as parent
  }
}
```

*Java*
```java
@Configuration
public class SubTokenConfig {
  @Bean
  public SubTokenRegistration deviceSubToken() {
    return new SubTokenRegistration() {
      @Override public String getName() { return "device"; }
      @Override public boolean isIncludeByDefault() { return false; }  // Only on explicit request
      @Override public String clientIdFor(String parent) { return parent + ".device"; }
      @Override public Instant expiry(Instant parentExpiry) {
        return parentExpiry.plus(30, ChronoUnit.DAYS);
      }
    };
  }

  @Bean
  public SubTokenRegistration apiSubToken() {
    return new SubTokenRegistration() {
      @Override public String getName() { return "api"; }
      @Override public boolean isIncludeByDefault() { return true; }  // Always included
      @Override public String clientIdFor(String parent) { return parent + ".api"; }
      @Override public Instant expiry(Instant parentExpiry) {
        return parentExpiry;  // Same as parent
      }
    };
  }
}
```

## Error handling considerations
- Prefer `SecurityServiceException` for user-facing auth failures (`error.auth.*` codes).
- Never log raw token values; use the helpers in `SecurityHelpers` when parsing or validating identifiers.

**Example - Custom Exception Handling:**

*Kotlin*
```kotlin
@ControllerAdvice
class SecurityExceptionHandler {
  @ExceptionHandler(SecurityServiceException::class)
  fun handleSecurityException(ex: SecurityServiceException): ResponseEntity<ErrorResponse> {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
      .body(ErrorResponse(ex.code, ex.message))
  }

  @ExceptionHandler(TokenExpiredException::class)
  fun handleTokenExpired(ex: TokenExpiredException): ResponseEntity<ErrorResponse> {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
      .body(ErrorResponse("error.auth.token_expired", "Token has expired. Please login again."))
  }
}
```

*Java*
```java
@ControllerAdvice
public class SecurityExceptionHandler {
  @ExceptionHandler(SecurityServiceException.class)
  public ResponseEntity<ErrorResponse> handleSecurityException(SecurityServiceException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
      .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(TokenExpiredException.class)
  public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
      .body(new ErrorResponse("error.auth.token_expired", "Token has expired. Please login again."));
  }
}
```

## Testing hooks
- In-memory fake repositories (`InMemoryTokenRepository` in tests) allow exercising rotation without a database.
- Batch window behavior is covered in `OgiriTokenAuthenticationFilterTest`; extend these cases when changing rotation or bypass logic.

**Example - Testing with In-Memory Repository:**

*Kotlin*
```kotlin
@SpringBootTest
class AuthenticationFlowTest {
  @Autowired
  private lateinit var tokenService: TokenService<TestToken>

  @MockBean
  private lateinit var tokenRepository: TokenRepository<TestToken>

  @Test
  fun testTokenRotation() {
    // Mock repository behavior
    every { tokenRepository.save(any()) } answers { firstArg() }
    every { tokenRepository.findByUserIdAndClient(any(), any()) } returns null

    val token = tokenService.createNewAuthToken(1L, "web-app")
    assertNotNull(token)
  }
}
```

*Java*
```java
@SpringBootTest
public class AuthenticationFlowTest {
  @Autowired
  private TokenService<TestToken> tokenService;

  @MockBean
  private TokenRepository<TestToken> tokenRepository;

  @Test
  public void testTokenRotation() {
    // Mock repository behavior
    when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    when(tokenRepository.findByUserIdAndClient(anyLong(), anyString())).thenReturn(null);

    TestToken token = tokenService.createNewAuthToken(1L, "web-app");
    assertNotNull(token);
  }
}
```
