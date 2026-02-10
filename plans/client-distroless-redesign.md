# Ogiri Client: Distroless Pluggable Redesign

## Context

The current `ogiri-client` is a monolithic HTTP client that wraps `fetch` with `get/post/put/delete` methods. Modern TS projects already have an HTTP stack (axios + react-query, ky, ofetch, etc.) and don't want a competing client. The library should provide **auth primitives** that plug into any existing stack, following the pattern used by Auth0 SPA SDK and MSAL.

**Requirements:**
- Zero mandatory runtime dependencies ("distroless")
- Consumers bring their own HTTP client (axios, ky, ofetch, native fetch)
- Ship a lightweight fetch wrapper as a convenience, not the primary API
- Separate entrypoint for axios adapter (tree-shaken if unused)

**Tooling:**
- **Build**: tsup (esbuild) — designed for libraries, no change needed
- **Tests**: Vitest (already Vite-native) — no change needed
- **Lint**: oxlint (replaces ESLint, Rust-based, ~50-100x faster)
- **Format**: oxfmt (replaces Prettier, Rust-based, ~30x faster, 95% Prettier compat)

---

## Architecture

### Core (`ogiri-security-client`) — zero dependencies

**`OgiriAuth`** class — the central piece. Manages token state and provides adapter factories:

```typescript
class OgiriAuth {
  constructor(config: { authMethod?: OgiriAuthMethod; storage?: TokenStorage })

  // Token state
  getTokens(): OgiriTokens | null
  setTokens(tokens: OgiriTokens): void
  clearTokens(): void
  isAuthenticated(): boolean
  onAuthError(callback: (error: OgiriAuthError) => void): void

  // Adapter factories
  createFetchClient(baseURL: string): OgiriFetchClient
  headerInjector(): (headers: Record<string, string>) => Record<string, string>

  // Low-level (for custom integrations)
  injectInto(config: RequestInit): RequestInit
  extractFrom(response: Response): void
}
```

**Pure functions** remain exported for BYO wiring:
- `injectAuth(config, tokens, method)` — unchanged
- `extractTokens(response)` — unchanged
- `MemoryTokenStorage`, `LocalStorageTokenStorage` — unchanged

**`OgiriFetchClient`** — thin convenience wrapper (delegates to OgiriAuth for state):
- `get/post/put/delete` over native fetch
- Auto-injects auth, auto-extracts rotated tokens
- Optional — consumers who use axios/ky ignore this entirely

### Axios adapter (`ogiri-security-client/axios`) — axios as peerDependency

```typescript
import { createAxiosInterceptors } from 'ogiri-security-client/axios'

const { request, response } = createAxiosInterceptors(auth)
axiosInstance.interceptors.request.use(request)
axiosInstance.interceptors.response.use(response.onFulfilled, response.onRejected)
```

---

## File Structure

```
ogiri-client/
├── src/
│   ├── index.ts              # Main entrypoint
│   ├── auth.ts               # NEW: OgiriAuth class
│   ├── fetch-client.ts       # RENAME from client.ts: OgiriFetchClient
│   ├── types.ts              # MODIFY: add OgiriAuthConfig
│   ├── errors.ts             # KEEP as-is
│   ├── interceptors.ts       # KEEP as-is (pure functions)
│   ├── token-storage.ts      # KEEP as-is
│   └── axios/
│       ├── index.ts          # Axios adapter entrypoint
│       └── interceptors.ts   # createAxiosInterceptors()
├── tests/
│   ├── auth.test.ts          # NEW
│   ├── fetch-client.test.ts  # RENAME from client.test.ts
│   ├── axios-interceptors.test.ts  # NEW
│   ├── interceptors.test.ts  # KEEP as-is
│   └── token-storage.test.ts # KEEP as-is
├── oxlintrc.json             # NEW: oxlint config
├── .oxfmtrc.json             # NEW: oxfmt config
├── tsup.config.ts            # MODIFY: two entrypoints
├── vitest.config.ts          # KEEP as-is
└── package.json              # MODIFY: exports, peerDeps, scripts
```

### Build: tsup with two entrypoints

```typescript
entry: ['src/index.ts', 'src/axios/index.ts']
```

### package.json exports

```json
{
  "exports": {
    ".": { "types": "./dist/index.d.ts", "import": "./dist/index.js", "require": "./dist/index.cjs" },
    "./axios": { "types": "./dist/axios/index.d.ts", "import": "./dist/axios/index.js", "require": "./dist/axios/index.cjs" }
  },
  "peerDependencies": { "axios": ">=1.0.0" },
  "peerDependenciesMeta": { "axios": { "optional": true } }
}
```

---

## Implementation Steps

### 1. Add oxlint + oxfmt tooling

- `pnpm add -D oxlint oxfmt`
- Create `oxlintrc.json` with rules appropriate for a library (no-console warn, type-aware checks)
- Create `.oxfmtrc.json` (Prettier-compat defaults: 2-space indent, single quotes, trailing commas)
- Update `package.json` scripts:
  - `"lint": "oxlint src/ tests/"`
  - `"format": "oxfmt src/ tests/"`
  - `"format:check": "oxfmt --check src/ tests/"`
- Remove any eslint/prettier config if present
- Format existing codebase with oxfmt to establish baseline

### 2. Create `src/auth.ts` — OgiriAuth class

- Constructor takes `{ authMethod?, storage?, onAuthError? }`
- Wraps token state management (get/set/clear/isAuthenticated)
- `injectInto(config: RequestInit)` — delegates to `injectAuth()` with stored tokens + authMethod
- `extractFrom(response: Response)` — calls `extractTokens()`, stores if present
- `headerInjector()` — returns function that adds auth headers to plain headers object
- `createFetchClient(baseURL)` — factory for OgiriFetchClient

### 3. Rename `src/client.ts` → `src/fetch-client.ts`

- Class renamed to `OgiriFetchClient`
- Constructor takes `OgiriAuth` + `baseURL` (no longer owns token state)
- `get/post/put/delete` delegate to `OgiriAuth.injectInto()` and `OgiriAuth.extractFrom()`

### 4. Create `src/axios/interceptors.ts`

- `createAxiosInterceptors(auth: OgiriAuth)` returns `{ request, response }`
- Request: reads tokens from auth, injects into `config.headers`
- Response: extracts rotated tokens, stores via auth
- Error: on 401, clears tokens, fires onAuthError, re-throws

### 5. Create `src/axios/index.ts`

- Re-exports `createAxiosInterceptors`

### 6. Update `src/index.ts`

- Export `OgiriAuth` as primary API
- Export `OgiriFetchClient` as convenience
- Keep exporting pure functions and types

### 7. Update `src/types.ts`

- Add `OgiriAuthConfig` interface
- Keep all existing types

### 8. Update `tsup.config.ts`

- Two entrypoints: `src/index.ts`, `src/axios/index.ts`

### 9. Update `package.json`

- Add `exports` map with `./axios` subpath
- Add `peerDependencies` for axios (optional)
- Add `axios` types to devDependencies
- Update scripts for oxlint/oxfmt

### 10. Update tests

- `tests/auth.test.ts` — OgiriAuth state, injectInto, extractFrom, headerInjector
- `tests/fetch-client.test.ts` — adapt client.test.ts to use OgiriAuth
- `tests/axios-interceptors.test.ts` — axios adapter request/response/401 handling
- `tests/interceptors.test.ts` — keep as-is
- `tests/token-storage.test.ts` — keep as-is

### 11. Update README.md

- Primary example: OgiriAuth + axios
- Secondary: OgiriAuth + fetch client
- Advanced: pure functions for BYO

---

## Consumer Usage Examples

### Axios + React Query
```typescript
import { OgiriAuth, LocalStorageTokenStorage } from 'ogiri-security-client'
import { createAxiosInterceptors } from 'ogiri-security-client/axios'
import axios from 'axios'

const auth = new OgiriAuth({
  storage: new LocalStorageTokenStorage(),
  onAuthError: () => router.push('/login'),
})

const api = axios.create({ baseURL: 'https://api.example.com' })
const { request, response } = createAxiosInterceptors(auth)
api.interceptors.request.use(request)
api.interceptors.response.use(response.onFulfilled, response.onRejected)

const { data } = useQuery({ queryKey: ['users'], queryFn: () => api.get('/users') })
```

### Built-in fetch client
```typescript
const auth = new OgiriAuth({ storage: new MemoryTokenStorage() })
const client = auth.createFetchClient('https://api.example.com')
const { data } = await client.post('/api/auth/login', { username, password })
```

### BYO HTTP client (ky, ofetch, etc.)
```typescript
const auth = new OgiriAuth()
const headers = auth.headerInjector()({ 'Content-Type': 'application/json' })
auth.extractFrom(response)
```

---

## Files Changed

| File | Action |
|------|--------|
| `src/auth.ts` | CREATE — OgiriAuth class |
| `src/fetch-client.ts` | CREATE (replaces client.ts) — OgiriFetchClient |
| `src/client.ts` | DELETE |
| `src/axios/index.ts` | CREATE — axios adapter entrypoint |
| `src/axios/interceptors.ts` | CREATE — createAxiosInterceptors |
| `src/index.ts` | MODIFY — new exports |
| `src/types.ts` | MODIFY — add OgiriAuthConfig |
| `src/interceptors.ts` | KEEP as-is |
| `src/token-storage.ts` | KEEP as-is |
| `src/errors.ts` | KEEP as-is |
| `oxlintrc.json` | CREATE — oxlint config |
| `.oxfmtrc.json` | CREATE — oxfmt config |
| `tsup.config.ts` | MODIFY — two entrypoints |
| `vitest.config.ts` | KEEP as-is |
| `package.json` | MODIFY — exports, peerDeps, scripts, devDeps |
| `tests/auth.test.ts` | CREATE |
| `tests/fetch-client.test.ts` | CREATE (replaces client.test.ts) |
| `tests/axios-interceptors.test.ts` | CREATE |
| `tests/interceptors.test.ts` | KEEP as-is |
| `tests/token-storage.test.ts` | KEEP as-is |
| `README.md` | REWRITE |

## Verification (Client Library)

1. `pnpm build` — both entrypoints compile, `dist/` has `index.*` and `axios/index.*`
2. `pnpm test` — all tests pass (vitest)
3. `pnpm typecheck` — no type errors
4. `pnpm lint` — oxlint passes
5. `pnpm format:check` — oxfmt passes
6. Verify `dist/` main entrypoint has no axios code
7. Verify pure functions (`injectAuth`, `extractTokens`) still exported from main entrypoint

---

# Part 2: Sample React App

## Context

Add a `sample/sample-react` app that demonstrates the pluggable client architecture with a real-world auth flow. Uses MSW to mock the Ogiri backend so it runs standalone without Spring Boot. Showcases axios + React Query integration (the primary consumer pattern).

## Tech Stack

| Tool | Purpose |
|------|---------|
| Vite | Dev server + build |
| React 19 | UI |
| React Router | Protected routes |
| Axios | HTTP client |
| TanStack React Query | Server state |
| MSW 2 | Mock API server (browser worker) |
| ogiri-security-client | Auth primitives (local workspace link) |
| oxlint + oxfmt | Lint + format (shared config with ogiri-client) |

No CSS framework — plain CSS modules. This is a library demo, not a design showcase.

## Features

1. **Login/Logout flow** — Form with username/password, token stored in localStorage, logout clears tokens
2. **Protected route** — `/dashboard` redirects to `/login` if unauthenticated
3. **Token rotation display** — Shows current token (truncated), highlights when it changes after a request
4. **Mock API server** — MSW handlers that replicate Ogiri's auth protocol (rotation on every response, batch detection, multi-auth-method support)

## File Structure

```
sample/sample-react/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── oxlintrc.json
├── public/
│   └── mockServiceWorker.js    # MSW service worker (generated)
├── src/
│   ├── main.tsx                # App entry, MSW init, QueryClientProvider
│   ├── App.tsx                 # Router setup
│   ├── api/
│   │   ├── client.ts           # OgiriAuth + axios instance + interceptors
│   │   └── queries.ts          # React Query hooks (useLogin, useLogout, useProfile, useDemoInfo)
│   ├── auth/
│   │   ├── AuthProvider.tsx     # React context wrapping OgiriAuth state
│   │   ├── useAuth.ts          # Hook: { isAuthenticated, tokens, login, logout }
│   │   └── ProtectedRoute.tsx  # Route guard component
│   ├── pages/
│   │   ├── LoginPage.tsx       # Login form
│   │   └── DashboardPage.tsx   # Protected page showing user info + token rotation
│   ├── components/
│   │   └── TokenDisplay.tsx    # Visual token rotation indicator
│   ├── mocks/
│   │   ├── browser.ts          # MSW browser setup
│   │   ├── handlers.ts         # MSW request handlers (login, logout, protected endpoints)
│   │   └── db.ts               # In-memory user/token database for mock server
│   └── styles/
│       └── app.css             # Minimal styles
└── README.md
```

## MSW Mock Server Design

The mock server replicates Ogiri's auth protocol discovered from sample-kotlin:

### `mocks/db.ts` — In-memory state

```typescript
interface MockUser { id: number; username: string; password: string; email: string }
interface MockSession { token: string; client: string; uid: string; expiry: string; lastRequestAt: number }

const users: MockUser[] = [
  { id: 1, username: 'user1', password: 'password', email: 'user1@example.com' },
  { id: 2, username: 'user2', password: 'password', email: 'user2@example.com' },
]

const sessions: Map<string, MockSession> = new Map()  // keyed by client ID
```

### `mocks/handlers.ts` — Endpoint handlers

| Endpoint | Behavior |
|----------|----------|
| `POST /api/auth/login` | Validates credentials, creates session, returns tokens in headers + body |
| `POST /api/auth/logout` | Extracts client from auth headers, deletes that session |
| `GET /api/me` | Returns user profile if authenticated, 401 otherwise |
| `GET /api/demo/info` | Returns demo data with **rotated tokens in response headers** |

Key behaviors:
- Every authenticated response returns new `access-token` + `expiry` headers (rotation)
- Batch detection: if last request was <30s ago, skip rotation headers
- Auth extraction: reads from `access-token`/`client`/`uid` headers (header method)
- 401 response clears the session

### `mocks/browser.ts`

```typescript
import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
export const worker = setupWorker(...handlers)
```

## Auth Integration (`src/api/client.ts`)

This is the showcase file — demonstrates the pluggable architecture:

```typescript
import { OgiriAuth, LocalStorageTokenStorage } from 'ogiri-security-client'
import { createAxiosInterceptors } from 'ogiri-security-client/axios'
import axios from 'axios'

export const auth = new OgiriAuth({
  authMethod: 'headers',
  storage: new LocalStorageTokenStorage(),
})

export const api = axios.create({ baseURL: '' })  // same-origin for MSW

const { request, response } = createAxiosInterceptors(auth)
api.interceptors.request.use(request)
api.interceptors.response.use(response.onFulfilled, response.onRejected)
```

## React Query Hooks (`src/api/queries.ts`)

```typescript
export function useLogin() {
  return useMutation({
    mutationFn: (creds: { username: string; password: string }) =>
      api.post('/api/auth/login', creds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['me'] }),
  })
}

export function useProfile() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => api.get('/api/me').then(r => r.data),
    enabled: auth.isAuthenticated(),
  })
}
```

## Auth Context (`src/auth/AuthProvider.tsx`)

Wraps `OgiriAuth` in React context so components can reactively access auth state:

```typescript
const AuthContext = createContext<{
  isAuthenticated: boolean
  tokens: OgiriTokens | null
  login: (tokens: OgiriTokens) => void
  logout: () => void
}>()
```

Uses `useSyncExternalStore` to subscribe to OgiriAuth state changes without polling.

## Token Rotation Display (`src/components/TokenDisplay.tsx`)

Shows:
- Current `access-token` (first 8 chars + `...`)
- Current `client` ID
- Current `expiry` timestamp
- Flash/highlight animation when token changes (detected via `usePrevious` comparison)

## Protected Route (`src/auth/ProtectedRoute.tsx`)

```typescript
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return children
}
```

## Implementation Steps

### 12. Scaffold sample-react project

- `pnpm create vite sample/sample-react --template react-ts`
- Add dependencies: `axios`, `@tanstack/react-query`, `react-router-dom`, `msw`
- Add devDependencies: `oxlint`, `oxfmt`
- Link ogiri-client: `"ogiri-security-client": "workspace:*"` in package.json
- Configure vite.config.ts, tsconfig.json

### 13. Create MSW mock server

- `mocks/db.ts` — users + session store
- `mocks/handlers.ts` — auth protocol handlers with rotation
- `mocks/browser.ts` — MSW browser worker
- Generate service worker: `npx msw init public/`

### 14. Create auth integration layer

- `api/client.ts` — OgiriAuth + axios + interceptors
- `api/queries.ts` — React Query hooks
- `auth/AuthProvider.tsx` — React context
- `auth/useAuth.ts` — convenience hook
- `auth/ProtectedRoute.tsx` — route guard

### 15. Create pages and components

- `pages/LoginPage.tsx` — login form, calls useLogin mutation
- `pages/DashboardPage.tsx` — protected, shows profile + token rotation demo
- `components/TokenDisplay.tsx` — live token display with rotation highlight

### 16. Wire app entry

- `main.tsx` — MSW init → React render → QueryClientProvider → AuthProvider → Router
- `App.tsx` — Route definitions

### 17. Add to workspace

- Update root `pnpm-workspace.yaml` (if exists) or root `package.json` workspaces
- Verify `pnpm install` resolves ogiri-security-client workspace link

## Verification (Sample React App)

1. `cd sample/sample-react && pnpm install`
2. `pnpm dev` — app starts, MSW logs "Mocking enabled" in console
3. Navigate to `/` → redirected to `/login`
4. Login with `user1`/`password` → redirected to `/dashboard`
5. Dashboard shows user info and current token
6. Click "Make Request" → token display updates (rotation visible)
7. Click "Logout" → redirected to `/login`, tokens cleared
8. Navigate to `/dashboard` directly → redirected to `/login`
9. `pnpm lint` and `pnpm format:check` pass
