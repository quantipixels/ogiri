# React Integration

This guide shows how to integrate Ogiri token authentication into a React app.

> **No npm package required.** Copy the two files from
> [`sample/sample-react/src/lib/`](../sample/sample-react/src/lib/) directly into your project.
> They have zero runtime dependencies.

---

## 1. Copy the auth primitives

Copy these two files into your project (e.g. `src/lib/`):

| File                                                              | Purpose                                                                                      |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| [`auth.ts`](../sample/sample-react/src/lib/auth.ts)               | Token types, storage, `OgiriAuth` state manager, pure `injectAuth`/`extractTokens` functions |
| [`axios-ogiri.ts`](../sample/sample-react/src/lib/axios-ogiri.ts) | Axios interceptors — only needed if you use axios                                            |

`auth.ts` has no external dependencies. `axios-ogiri.ts` depends only on axios (which you already have).

---

## 2. Create the auth instance

```typescript
// src/api/client.ts
import { OgiriAuth, LocalStorageTokenStorage } from "../lib/auth";
import { createAxiosInterceptors } from "../lib/axios-ogiri";
import axios from "axios";

export const auth = new OgiriAuth({
  authMethod: "headers", // or "bearer" — match your server config
  storage: new LocalStorageTokenStorage(), // persists across page reloads
});

export const api = axios.create({ baseURL: "https://api.example.com" });

const { request, response } = createAxiosInterceptors(auth);
api.interceptors.request.use(request);
api.interceptors.response.use(response.onFulfilled, response.onRejected);
```

The interceptors handle three things automatically:

- **Request**: injects `access-token`, `client`, `uid`, `expiry`, `token-type` headers
- **Response**: extracts rotated token headers and updates stored tokens
- **401**: clears stored tokens and fires `onAuthError` if configured

---

## 3. Expose auth state to React with `useSyncExternalStore`

`OgiriAuth` is a plain event emitter (`subscribe`/`notify`). Hook it into React's external store
primitive so components re-render when tokens change:

```tsx
// src/auth/AuthProvider.tsx
import {
  createContext,
  useCallback,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { auth } from "../api/client";
import type { OgiriTokens } from "../lib/auth";

interface AuthContextValue {
  isAuthenticated: boolean;
  tokens: OgiriTokens | null;
  login: (tokens: OgiriTokens) => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const tokens = useSyncExternalStore(
    (cb) => auth.subscribe(cb), // subscribe
    () => auth.getTokens() // getSnapshot
  );

  const login = useCallback((t: OgiriTokens) => auth.setTokens(t), []);
  const logout = useCallback(() => auth.clearTokens(), []);

  const value = useMemo(
    () => ({ isAuthenticated: tokens !== null, tokens, login, logout }),
    [tokens, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
```

`useSyncExternalStore` is the React 18+ standard for subscribing to non-React state. It correctly
handles concurrent rendering without tearing.

---

## 4. Consume auth state in components

```tsx
// src/auth/useAuth.ts
import { useContext } from "react";
import { AuthContext } from "./AuthProvider";

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
```

```tsx
// Protected route
function ProtectedRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return children;
}
```

---

## 5. Handle login

After a successful login POST, the server returns tokens in the response body **and** sets them in
response headers (for rotation). Store whichever you receive:

```typescript
// src/api/queries.ts
export function useLogin() {
  return useMutation({
    mutationFn: (creds: { username: string; password: string }) =>
      api.post("/api/auth/login", creds),
    onSuccess: ({ data }) => {
      // Map server response fields to OgiriTokens
      auth.setTokens({
        accessToken: data["access-token"],
        client: data.client,
        uid: data.uid,
        expiry: data.expiry,
        tokenType: data["token-type"],
      });
    },
  });
}
```

After login, every subsequent request automatically carries the current tokens. When the server
rotates tokens (sends new headers), the response interceptor updates storage and notifies
`AuthProvider` — components re-render with the new tokens without any extra code.

---

## 6. Token rotation display

The sample app demonstrates live token rotation using a `prevToken` ref:

```tsx
// src/components/TokenDisplay.tsx — from sample-react
export function TokenDisplay({ tokens }: { tokens: OgiriTokens | null }) {
  const prevRef = useRef<string | null>(null);
  const [highlight, setHighlight] = useState(false);

  useEffect(() => {
    const current = tokens?.accessToken ?? null;
    if (prevRef.current !== null && prevRef.current !== current) {
      setHighlight(true);
      const t = setTimeout(() => setHighlight(false), 1000);
      return () => clearTimeout(t);
    }
    prevRef.current = current;
  }, [tokens?.accessToken]);

  if (!tokens) return <p>No tokens</p>;
  return (
    <div className={highlight ? "rotated" : ""}>
      <code>{tokens.accessToken.substring(0, 8)}…</code>
    </div>
  );
}
```

---

## 7. HTTP clients

`OgiriAuth` exposes three primitives that map onto any HTTP client:

| Primitive                    | What it does                                                       |
| ---------------------------- | ------------------------------------------------------------------ |
| `auth.injectInto(config)`    | Returns a new `RequestInit` with auth headers merged in            |
| `auth.extractFrom(response)` | Reads rotation headers from a `Response` and updates stored tokens |
| `auth.headerInjector()`      | Returns `(headers) => headers` — merges auth into a plain object   |

=== "axios"

    Full source: [`sample/sample-react/src/api/client.ts`](../sample/sample-react/src/api/client.ts)
    and [`src/lib/axios-ogiri.ts`](../sample/sample-react/src/lib/axios-ogiri.ts).

    ```typescript
    import { OgiriAuth, LocalStorageTokenStorage } from "./lib/auth";
    import { createAxiosInterceptors } from "./lib/axios-ogiri";
    import axios from "axios";

    export const auth = new OgiriAuth({
        authMethod: "headers",
        storage: new LocalStorageTokenStorage(),
    });

    export const api = axios.create({ baseURL: "https://api.example.com" });

    const { request, response } = createAxiosInterceptors(auth);
    api.interceptors.request.use(request);
    api.interceptors.response.use(response.onFulfilled, response.onRejected);
    ```

    The `createAxiosInterceptors` function in `axios-ogiri.ts` bridges axios's non-standard
    `InternalAxiosRequestConfig`/`AxiosResponse` types to the `RequestInit`/`Response` primitives
    that `auth.injectInto()` and `auth.extractFrom()` expect.

=== "fetch"

    No adapter file needed — `auth.injectInto()` and `auth.extractFrom()` accept the standard
    Fetch API types directly.

    ```typescript
    // src/api/client.ts
    import { OgiriAuth, LocalStorageTokenStorage, OgiriAuthError } from "./lib/auth";

    export const auth = new OgiriAuth({
        authMethod: "headers",
        storage: new LocalStorageTokenStorage(),
    });

    export async function ogiriFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
        const response = await fetch(
            `https://api.example.com${path}`,
            auth.injectInto(options),
        );

        if (response.status === 401) {
            const body = await response.json().catch(() => null);
            throw auth.handleAuthError(body);
        }

        if (!response.ok) {
            throw new Error(
                `${options.method ?? "GET"} ${path} failed: ${response.status} ${response.statusText}`,
            );
        }

        auth.extractFrom(response.clone()); // clone before consuming body
        return response.json() as Promise<T>;
    }
    ```

    Login — tokens arrive in the response body on the first request, then rotate via headers:

    ```typescript
    const data = await ogiriFetch<LoginResponse>("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
    });
    // ogiriFetch calls auth.extractFrom() — tokens stored automatically
    ```

=== "ky"

    ky uses standard `Request`/`Response` in its hook API, so no adapter file is needed.
    Use `afterResponse` (not `afterResponseError` — that hook does not exist in ky) to handle
    both rotation and 401s:

    ```typescript
    import ky from "ky";
    import { OgiriAuth, LocalStorageTokenStorage } from "./lib/auth";

    export const auth = new OgiriAuth({
        authMethod: "headers",
        storage: new LocalStorageTokenStorage(),
    });

    export const api = ky.create({
        prefixUrl: "https://api.example.com",
        hooks: {
            beforeRequest: [
                (request) => {
                    for (const [key, value] of Object.entries(auth.headerInjector()({}))) {
                        request.headers.set(key, value);
                    }
                },
            ],
            afterResponse: [
                (_, __, response) => {
                    if (response.status === 401) {
                        auth.handleAuthError(null);
                    } else {
                        auth.extractFrom(response.clone());
                    }
                    return response;
                },
            ],
        },
    });
    ```

=== "ofetch"

    `ofetch`'s `FetchResponse` extends the standard `Response`, so `auth.extractFrom()` accepts
    it directly. `onResponseError` handles 401s separately from successful responses:

    ```typescript
    import { $fetch, type FetchOptions } from "ofetch";
    import { OgiriAuth, LocalStorageTokenStorage } from "./lib/auth";

    export const auth = new OgiriAuth({
        authMethod: "headers",
        storage: new LocalStorageTokenStorage(),
    });

    const baseOptions: FetchOptions = {
        baseURL: "https://api.example.com",
        onRequest: ({ options }) => {
            options.headers = { ...(options.headers as object), ...auth.headerInjector()({}) };
        },
        onResponse: ({ response }) => {
            auth.extractFrom(response);
        },
        onResponseError: ({ response }) => {
            if (response.status === 401) auth.handleAuthError(null);
        },
    };

    export const api = $fetch.create(baseOptions);
    ```

---

## 8. React state management

`OgiriAuth` is a plain observable — it has a `subscribe(listener)` method that fires whenever
tokens change (login, logout, or rotation from an HTTP response). Any state manager can bridge it
with a one-line subscription.

=== "useSyncExternalStore"

    The built-in React 18 primitive. No extra dependencies.
    Full source: [`sample/sample-react/src/auth/AuthProvider.tsx`](../sample/sample-react/src/auth/AuthProvider.tsx)

    ```tsx
    // src/auth/AuthProvider.tsx
    import { createContext, useCallback, useMemo, useSyncExternalStore, type ReactNode } from "react";
    import { auth } from "../api/client";
    import type { OgiriTokens } from "../lib/auth";

    interface AuthContextValue {
        isAuthenticated: boolean;
        tokens: OgiriTokens | null;
        login: (tokens: OgiriTokens) => void;
        logout: () => void;
    }

    export const AuthContext = createContext<AuthContextValue | null>(null);

    export function AuthProvider({ children }: { children: ReactNode }) {
        const tokens = useSyncExternalStore(
            (cb) => auth.subscribe(cb),  // subscribe — returns unsubscribe fn
            () => auth.getTokens(),      // getSnapshot
        );

        const login  = useCallback((t: OgiriTokens) => auth.setTokens(t), []);
        const logout = useCallback(() => auth.clearTokens(), []);
        const value  = useMemo(
            () => ({ isAuthenticated: tokens !== null, tokens, login, logout }),
            [tokens, login, logout],
        );

        return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
    }
    ```

    ```tsx
    // src/auth/useAuth.ts — full source in sample-react
    import { useContext } from "react";
    import { AuthContext } from "./AuthProvider";

    export function useAuth() {
        const ctx = useContext(AuthContext);
        if (!ctx) throw new Error("useAuth must be used within AuthProvider");
        return ctx;
    }
    ```

    `useSyncExternalStore` guarantees tear-free reads under React 18 concurrent rendering.
    `auth.subscribe` returns an unsubscribe function, which is exactly what React expects.

=== "Zustand"

    Bridge `OgiriAuth`'s `subscribe` into a Zustand store. Keep `OgiriAuth` as the source of
    truth — the store is a reactive read layer, not a second state owner.

    ```typescript
    // src/store/auth-store.ts
    import { create } from "zustand";
    import { auth } from "../api/client";
    import type { OgiriTokens } from "../lib/auth";

    interface AuthStore {
        tokens: OgiriTokens | null;
        isAuthenticated: boolean;
        login: (tokens: OgiriTokens) => void;
        logout: () => void;
    }

    export const useAuthStore = create<AuthStore>(() => ({
        tokens: auth.getTokens(),
        isAuthenticated: auth.isAuthenticated(),
        login:  (tokens) => auth.setTokens(tokens),
        logout: ()       => auth.clearTokens(),
    }));

    // Keep the store in sync with OgiriAuth (rotation from interceptors, external logouts, etc.)
    auth.subscribe(() =>
        useAuthStore.setState({
            tokens: auth.getTokens(),
            isAuthenticated: auth.isAuthenticated(),
        }),
    );
    ```

    In components:

    ```tsx
    import { useAuthStore } from "../store/auth-store";

    function Navbar() {
        const { isAuthenticated, logout } = useAuthStore();
        return isAuthenticated ? <button onClick={logout}>Logout</button> : null;
    }

    // Select only what you need to avoid unnecessary re-renders
    function TokenBadge() {
        const tokens = useAuthStore((s) => s.tokens);
        return tokens ? <code>{tokens.accessToken.substring(0, 8)}…</code> : null;
    }
    ```

    Login — call `auth.setTokens()` (or the store's `login` action) after receiving tokens from
    the login endpoint. The `subscribe` callback updates the store automatically:

    ```typescript
    const { login } = useAuthStore.getState();
    login({ accessToken: data["access-token"], client: data.client, ... });
    // → auth.setTokens() fires → subscribe callback → store re-renders
    ```

=== "TanStack Store"

    `@tanstack/store` + `@tanstack/react-store`. Same pattern as Zustand: `OgiriAuth` owns the
    data, the Store is the reactive bridge.

    ```typescript
    // src/store/auth-store.ts
    import { Store } from "@tanstack/store";
    import { useStore } from "@tanstack/react-store";
    import { auth } from "../api/client";
    import type { OgiriTokens } from "../lib/auth";

    interface AuthState {
        tokens: OgiriTokens | null;
    }

    export const authStore = new Store<AuthState>({
        tokens: auth.getTokens(),
    });

    // Keep the store in sync with OgiriAuth
    auth.subscribe(() =>
        authStore.setState(() => ({ tokens: auth.getTokens() })),
    );

    // Derived selectors
    export const useTokens         = () => useStore(authStore, (s) => s.tokens);
    export const useIsAuthenticated = () => useStore(authStore, (s) => s.tokens !== null);
    ```

    Actions stay on `OgiriAuth` directly — no need to duplicate them on the store:

    ```typescript
    // Login
    auth.setTokens({ accessToken: data["access-token"], client: data.client, ... });

    // Logout
    auth.clearTokens();
    ```

    In components:

    ```tsx
    import { useTokens, useIsAuthenticated } from "../store/auth-store";

    function Navbar() {
        const isAuthenticated = useIsAuthenticated();
        return isAuthenticated ? <button onClick={() => auth.clearTokens()}>Logout</button> : null;
    }

    function TokenBadge() {
        const tokens = useTokens();
        return tokens ? <code>{tokens.accessToken.substring(0, 8)}…</code> : null;
    }
    ```

    `useStore` accepts a selector function, so components only re-render when the selected slice
    changes — equivalent to Zustand's selector pattern.

---

## Full working example

See [`sample/sample-react/`](../sample/sample-react/) for a complete app demonstrating:

- Login / logout flow with axios + React Query
- Protected routes with React Router
- Token rotation visualisation ([`src/components/TokenDisplay.tsx`](../sample/sample-react/src/components/TokenDisplay.tsx))
- MSW mock server replicating the full Ogiri auth protocol
- `useSyncExternalStore` bridging `OgiriAuth` to React context

Run it standalone (no Spring Boot required):

```bash
cd sample/sample-react
pnpm install
pnpm dev
# → http://localhost:5173
# Login: user1 / password
```
