# Security Guidelines

## Critical Rules

⚠️ **NEVER log raw tokens, passwords, or credentials**

## Logging & Secrets

- **Never log**: Raw tokens, credentials, PII
- **Redact** token values using `SecurityHelpers` utilities
- **Keep debug logging off** by default in library code

## Error Handling

- **Wrap** authentication/validation errors with `SecurityServiceException` to avoid leaking details
- **Descriptive messages** without sensitive data
- **Use guard clauses** for invalid input
- **Return 4xx vs 5xx** appropriately in web filters
- **Do not swallow exceptions** - log and rethrow domain-specific errors

## Security Invariants

### Token Storage & Comparison

- Tokens **BCrypt-hashed** before storage (never plaintext)
- Token comparison results **cached** (Caffeine, 1hr) to reduce BCrypt overhead
- Token prefix (8-char) enables **O(1) DB lookups** vs O(n) BCrypt scans

### Authentication Flow

1. Request → `OgiriTokenAuthenticationFilter`
2. `AuthenticationBypassDecider.canSkip()` checks public routes
3. Extract headers via `extractAuthHeader()` (headers → cookies → Bearer)
4. Validate token via `OgiriTokenService.validToken()`
5. Batch detection determines rotation need
6. Response headers set via `appendAuthHeaders()`

### Token Lifecycle

- **Grace period** allows batch requests without token thrashing
- **Batch window** (`batch-grace-seconds`) prevents rotation during concurrent requests
- **Staleness threshold** (`rotate-stale-seconds`) forces rotation after inactivity
- **Token rotation** preserves semantics; adjust tests if logic changes

### Sub-tokens

- **Scoped to parent** APP token
- Require `OgiriSubTokenRegistration` bean
- Add tests in `TokenServiceSubTokenTest` for new sub-token types

### Request Validation

- **Bearer token size** validated (8KB default) to prevent memory exhaustion
- Run `IdentifierPolicy` before database queries to validate identifiers

### Cookie Security

- **Authentication cookies cleared** on 401 (maxAge=0)
- Prevents stale-cookie loops (OWASP compliant)
- Controlled via `ogiri.cookies.enabled=true`

### Public Routes

- **`AuthenticationBypassDecider`** covers public routes
- Ensure bypass logic stays accurate to prevent lockouts
- Auth header keys are **API contracts** - changing requires migration notes

## Web Layer

- **`OgiriTokenAuthenticationFilter`** is central - keep request lifecycle stable
- Avoid adding new filters without documenting ordering requirements
