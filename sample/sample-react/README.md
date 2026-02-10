# sample-react

Demonstrates the pluggable ogiri-security-client architecture with a real-world auth flow.

Uses MSW to mock the Ogiri backend so it runs standalone without Spring Boot.

## Tech Stack

- Vite + React 19 + TypeScript
- Axios + TanStack React Query
- MSW 2 (mock API server)
- ogiri-security-client (workspace link)

## Run

```bash
pnpm install
pnpm dev
```

Navigate to http://localhost:5173. Login with `user1` / `password`.

## What It Demonstrates

- **OgiriAuth + axios interceptors** — the primary consumer pattern (`src/api/client.ts`)
- **Token rotation** — visible on dashboard when making requests
- **Protected routes** — unauthenticated users redirected to login
- **React context + useSyncExternalStore** — reactive auth state without polling
- **MSW mock server** — replicates Ogiri auth protocol (token rotation, session management)

## Available Commands

```bash
pnpm dev           # Dev server
pnpm build         # Production build
pnpm lint          # Lint with oxlint
pnpm format:check  # Check formatting with oxfmt
```
