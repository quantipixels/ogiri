# React Sample Application - Claude Code Instructions

## Overview

Sample React application demonstrating ogiri-security-client integration with a real-world auth flow. Uses MSW to mock the Ogiri backend so it runs standalone without Spring Boot.

**Language:** TypeScript
**Framework:** React 19 + Vite 6
**State:** TanStack React Query + useSyncExternalStore
**Mock Server:** MSW 2 (Service Worker)
**Auth Client:** ogiri-security-client (workspace link)

## Quick Commands

```bash
# Install dependencies
pnpm install

# Start dev server
pnpm dev

# Production build
pnpm build

# Lint with oxlint
pnpm lint

# Check formatting with oxfmt
pnpm format:check

# Fix formatting
pnpm format
```

## Project Structure

See `src/` for project layout.

## Running the App

```bash
pnpm install
pnpm dev
```

Navigate to http://localhost:5173. Login with `user1` / `password`.

## Available Routes

| Route        | Auth Required | Description                      |
| ------------ | ------------- | -------------------------------- |
| `/login`     | No            | Login form (redirects if authed) |
| `/dashboard` | Yes           | Profile + token rotation demo    |
| `*`          | No            | Redirects to `/login`            |

## Mock API Endpoints

MSW intercepts these requests client-side:

| Endpoint           | Method | Auth Required | Description                 |
| ------------------ | ------ | ------------- | --------------------------- |
| `/api/auth/login`  | POST   | No            | Login, returns tokens       |
| `/api/auth/logout` | POST   | Yes           | Logout, deletes session     |
| `/api/me`          | GET    | Yes           | Current user profile        |
| `/api/demo/info`   | GET    | Yes           | Demo endpoint with rotation |

## Test Users

| Username | Password | Email             |
| -------- | -------- | ----------------- |
| user1    | password | user1@example.com |
| user2    | password | user2@example.com |

## Integration Points

### OgiriAuth + Axios Interceptors

The primary consumer pattern for ogiri-security-client (`src/api/client.ts`):

```typescript
export const auth = new OgiriAuth({
  authMethod: "headers",
  storage: new LocalStorageTokenStorage(),
});

const { request, response } = createAxiosInterceptors(auth);
api.interceptors.request.use(request);
api.interceptors.response.use(response.onFulfilled, response.onRejected);
```

### Reactive Auth State

`AuthProvider` bridges OgiriAuth's subscription API to React via `useSyncExternalStore` (`src/auth/AuthProvider.tsx`):

```typescript
const tokens = useSyncExternalStore(
  (cb) => auth.subscribe(cb),
  () => auth.getTokens()
);
```

### Token Rotation

The MSW mock replicates Ogiri's rotation protocol:

- Rotation skipped if last request was < 30s ago (batch grace)
- New `access-token` returned in response headers when rotated
- Axios response interceptor picks up rotated tokens automatically
- `TokenDisplay` component highlights when rotation occurs

### Login Flow

1. `useLogin` mutation posts credentials to `/api/auth/login`
2. Response mapped to `OgiriTokens` (kebab-case headers to camelCase)
3. `auth.setTokens()` stores tokens and notifies subscribers
4. React Query cache invalidated to refetch with new auth

## MSW Mock Server

The mock server (`src/mocks/`) implements a minimal Ogiri-compatible backend:

- **db.ts** - In-memory user table and session map with token rotation logic
- **handlers.ts** - MSW request handlers that validate auth headers and rotate tokens
- **browser.ts** - Service Worker setup, started before React renders

MSW starts unconditionally in `main.tsx` via dynamic import. All API requests are intercepted client-side.

## Development Tips

- **No backend needed:** MSW handles all API calls in the browser
- **Token rotation visible:** Click "Make Request" on dashboard; tokens rotate after 30s grace period
- **Auth state reactive:** Logging in/out updates all components immediately via useSyncExternalStore
- **oxlint/oxfmt:** Uses Oxc toolchain for linting and formatting (not ESLint/Prettier)

## See Also

- Client library: `../../ogiri-client/`
- Java sample: `../sample-java/`
- Kotlin sample: `../sample-kotlin/`
- API documentation: `../../docs/`
