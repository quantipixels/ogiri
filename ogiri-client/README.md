# Ògiri Security Client

Pluggable auth primitives for Ògiri opaque token authentication. Zero mandatory runtime dependencies.

## Status

v2.0.0

## Architecture

- `OgiriAuth` is the central class - manages token state, provides adapter factories
- Ship a lightweight fetch wrapper (`OgiriFetchClient`) as a convenience
- Separate `ogiri-security-client/axios` entrypoint for axios adapter (tree-shaken if unused)
- Pure functions (`injectAuth`, `extractTokens`) still exported for custom integrations

## Primary Example: OgiriAuth + Axios

Main consumer pattern with axios + React Query:

```typescript
import { OgiriAuth, LocalStorageTokenStorage } from "ogiri-security-client";
import { createAxiosInterceptors } from "ogiri-security-client/axios";
import axios from "axios";

const auth = new OgiriAuth({
  storage: new LocalStorageTokenStorage(),
  onAuthError: () => router.push("/login"),
});

const api = axios.create({ baseURL: "https://api.example.com" });
const { request, response } = createAxiosInterceptors(auth);
api.interceptors.request.use(request);
api.interceptors.response.use(response.onFulfilled, response.onRejected);
```

## Secondary: Built-in Fetch Client

```typescript
const auth = new OgiriAuth({ storage: new MemoryTokenStorage() });
const client = auth.createFetchClient("https://api.example.com");
const { data } = await client.post("/api/auth/login", { username, password });
```

## Advanced: Pure Functions (BYO HTTP Client)

```typescript
const auth = new OgiriAuth();
const headers = auth.headerInjector()({ "Content-Type": "application/json" });
// After response:
auth.extractFrom(response);
```

## API Reference

### OgiriAuth

**Constructor:**

```typescript
interface OgiriAuthConfig {
  authMethod?: "headers" | "bearer" | "cookies"; // Default: 'headers'
  storage?: TokenStorage; // Default: MemoryTokenStorage
  onAuthError?: (error: OgiriAuthError) => void;
}
```

**Token state:**

- `getTokens()` - Returns current token state
- `setTokens(tokens)` - Manually set tokens
- `clearTokens()` - Clear all tokens
- `isAuthenticated()` - Check if user has valid tokens

**Adapter factories:**

- `createFetchClient(baseURL)` - Create OgiriFetchClient instance
- `headerInjector()` - Returns function to inject tokens into headers

**Low-level:**

- `injectInto(config)` - Inject tokens into RequestInit
- `extractFrom(response)` - Extract rotated tokens from Response
- `handleAuthError(body)` - Process auth error response

**Events:**

- `onAuthError(callback)` - Register auth error handler
- `subscribe(listener)` - Subscribe to token state changes

### OgiriFetchClient

HTTP client built on native fetch:

- `get(path, options?)` - GET request
- `post(path, options?)` - POST request
- `put(path, options?)` - PUT request
- `delete(path, options?)` - DELETE request

Auto-injects auth headers, auto-extracts rotated tokens.

### createAxiosInterceptors(auth)

Returns `{ request, response }` interceptors for axios:

- Handles token injection
- Extracts rotation tokens from responses
- Processes 401 errors with `handleAuthError`

### Pure Functions

Direct exports for custom integrations:

- `injectAuth(config, tokens, method)` - Inject tokens into RequestInit
- `extractTokens(response)` - Extract rotated tokens from Response

### Token Storage

**MemoryTokenStorage** - In-memory storage (SSR-safe, default)
**LocalStorageTokenStorage** - Browser localStorage

Custom storage must implement:

```typescript
interface TokenStorage {
  get(): OgiriTokens | null;
  set(tokens: OgiriTokens): void;
  clear(): void;
}
```

## Auth Methods

- `'headers'` (default) - Sends access-token/client/uid/expiry/token-type as individual headers
- `'bearer'` - Base64-encoded JSON token in Authorization header
- `'cookies'` - Cookie-based (server-side only; browsers ignore programmatic Cookie headers)

## Development

```bash
pnpm install
pnpm build
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
```

## Security Considerations

- Type safety at boundaries - validates JSON responses before casting
- Storage failure resilience - handles QuotaExceededError gracefully
- Network error context - detailed error messages with method + URL
- No plaintext tokens in logs

## License

Apache License 2.0
