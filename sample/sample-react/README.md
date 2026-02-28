# sample-react

Demonstrates Ogiri token authentication in a React app with no external auth library.

Runs standalone against an MSW mock server, or against a real Spring Boot backend (Java or Kotlin) via `run-live.sh`.

## Tech Stack

- Vite + React 19 + TypeScript
- Axios + TanStack React Query
- MSW 2 (mock API server)
- `src/lib/auth.ts` — inlined auth primitives (copy into your own project)
- `src/lib/axios-ogiri.ts` — axios interceptor wiring (copy alongside `auth.ts`)

## Run (mock backend)

```bash
pnpm install
pnpm dev
```

Navigate to http://localhost:5173. Login with `user1@example.com` / `password`.

A **mock-server** badge appears in the dashboard header confirming the active backend.

## Run (live backend)

`run-live.sh` starts the Spring Boot server, waits for it to be ready, then either runs vitest integration tests or opens a proxied Vite dev server.

```bash
# Vitest integration tests (default: kotlin)
./run-live.sh [java|kotlin]

# Vite dev server proxied to the real backend
./run-live.sh [java|kotlin] --ui
```

In `--ui` mode, MSW is bypassed and `/api/*` is proxied to the Spring Boot server. The dashboard badge shows **kotlin-server** or **java-server**.

## What It Demonstrates

- **OgiriAuth + axios interceptors** — the primary consumer pattern (`src/api/client.ts`)
- **Token rotation** — visible on the dashboard when making requests; rotated `access-token` highlighted on change
- **Expiry flow** — "Expire Token" button backdates the session expiry; the next request returns 401 and redirects to login
- **Protected routes** — unauthenticated users redirected to login
- **React context + useSyncExternalStore** — reactive auth state without polling
- **MSW mock server** — replicates the Ogiri auth protocol (login, token rotation, session management)
- **Backend badge** — pill in the dashboard header identifying the active backend (`mock-server` / `kotlin-server` / `java-server`)

## Default Credentials

| Field    | Value               |
| -------- | ------------------- |
| Email    | `user1@example.com` |
| Password | `password`          |

A second account (`user2@example.com` / `password`) is also available.

## Available Commands

```bash
pnpm dev           # Dev server (MSW mock backend)
pnpm build         # Production build
pnpm test          # Unit tests (db.test.ts, auth.integration.test.ts skipped)
pnpm lint          # Lint with oxlint
pnpm format:check  # Check formatting with oxfmt
```

Live tests are run via `run-live.sh`, not directly via `pnpm test:live`.
