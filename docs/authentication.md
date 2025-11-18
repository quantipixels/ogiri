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

## Sub-tokens
- Register `SubTokenRegistration` beans to mint protocol-specific tokens alongside APP tokens.
- `TokenService.issueSubTokens` respects `includeByDefault`; `renewSubToken` forces rotation for a specific sub-token and appends updated headers.
- Sub-token bearer decoding uses Base64 JSON payloads (`client`, `token`, `expiry`); server-side expiry is always enforced.

## Error handling considerations
- Prefer `SecurityServiceException` for user-facing auth failures (`error.auth.*` codes).
- Never log raw token values; use the helpers in `SecurityHelpers` when parsing or validating identifiers.

## Testing hooks
- In-memory fake repositories (`InMemoryTokenRepository` in tests) allow exercising rotation without a database.
- Batch window behavior is covered in `OgiriTokenAuthenticationFilterTest`; extend these cases when changing rotation or bypass logic.
