/**
 * Ogiri auth primitives — copy this file into your project.
 *
 * No external dependencies. Works with any HTTP client.
 * See docs/react-integration.md for usage examples.
 */

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface OgiriTokens {
    accessToken: string;
    client: string;
    uid: string;
    expiry: string;
    tokenType: string;
    /** Optional token kind (APP, SUB, etc.) */
    tokenKind?: string;
}

export type OgiriAuthMethod = "headers" | "bearer" | "cookies";

export interface TokenStorage {
    get(): OgiriTokens | null;
    set(tokens: OgiriTokens): void;
    clear(): void;
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

export class OgiriAuthError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly body: unknown,
    ) {
        super(message);
        this.name = "OgiriAuthError";
    }
}

// ---------------------------------------------------------------------------
// Storage implementations
// ---------------------------------------------------------------------------

/** In-memory storage — safe for SSR, lost on page reload. */
export class MemoryTokenStorage implements TokenStorage {
    private tokens: OgiriTokens | null = null;
    get(): OgiriTokens | null {
        return this.tokens;
    }
    set(tokens: OgiriTokens): void {
        this.tokens = tokens;
    }
    clear(): void {
        this.tokens = null;
    }
}

/**
 * localStorage — persists across page reloads, browser-only.
 *
 * get() returns the in-memory cache directly (stable reference).
 * Re-parsing JSON on every call would produce a new object each time,
 * causing useSyncExternalStore to see the snapshot as perpetually changed
 * and throw "Maximum update depth exceeded".
 */
export class LocalStorageTokenStorage implements TokenStorage {
    private _cache: OgiriTokens | null;

    constructor(private readonly key: string = "ogiri-tokens") {
        try {
            const stored = localStorage.getItem(key);
            this._cache = stored ? JSON.parse(stored) : null;
        } catch {
            this._cache = null;
        }
    }

    get(): OgiriTokens | null {
        return this._cache;
    }

    set(tokens: OgiriTokens): void {
        try {
            localStorage.setItem(this.key, JSON.stringify(tokens));
            this._cache = tokens;
        } catch (err) {
            console.error("Failed to save tokens to localStorage:", err instanceof Error ? err.message : String(err));
        }
    }

    clear(): void {
        try {
            localStorage.removeItem(this.key);
            this._cache = null;
        } catch (err) {
            console.error(
                "Failed to clear tokens from localStorage:",
                err instanceof Error ? err.message : String(err),
            );
        }
    }
}

// ---------------------------------------------------------------------------
// Pure functions — inject auth into a request, extract rotated tokens from a response
// ---------------------------------------------------------------------------

/**
 * Inject Ogiri auth tokens into a fetch RequestInit.
 * Returns a new config object; does not mutate the original.
 */
export function injectAuth(config: RequestInit, tokens: OgiriTokens, method: OgiriAuthMethod): RequestInit {
    const base = normalizeHeaders(config.headers);

    if (method === "headers") {
        return {
            ...config,
            headers: {
                ...base,
                "access-token": tokens.accessToken,
                client: tokens.client,
                uid: tokens.uid,
                expiry: tokens.expiry,
                "token-type": tokens.tokenType,
                ...(tokens.tokenKind ? { "access-token-kind": tokens.tokenKind } : {}),
            },
        };
    }

    if (method === "bearer") {
        const payload: Record<string, string> = {
            "access-token": tokens.accessToken,
            client: tokens.client,
            uid: tokens.uid,
            "token-type": tokens.tokenType,
            expiry: tokens.expiry,
            ...(tokens.tokenKind ? { "access-token-kind": tokens.tokenKind } : {}),
        };
        const base64 = btoa(JSON.stringify(payload));
        return { ...config, headers: { ...base, Authorization: `Bearer ${base64}` } };
    }

    if (method === "cookies") {
        if (typeof window !== "undefined") {
            console.warn("Cookie auth is not supported in browsers — use 'headers' or 'bearer' instead.");
        }
        const parts = [
            `access-token=${tokens.accessToken}`,
            `client=${tokens.client}`,
            `uid=${tokens.uid}`,
            `expiry=${tokens.expiry}`,
            `token-type=${tokens.tokenType}`,
            ...(tokens.tokenKind ? [`access-token-kind=${tokens.tokenKind}`] : []),
        ];
        return { ...config, credentials: "include", headers: { ...base, Cookie: parts.join("; ") } };
    }

    const _exhaustive: never = method;
    throw new Error(`Unknown auth method: ${String(_exhaustive)}`);
}

/**
 * Extract rotated tokens from a fetch Response's headers.
 * Returns null if the response does not carry rotation headers.
 */
export function extractTokens(response: Response): OgiriTokens | null {
    const accessToken = response.headers.get("access-token");
    const client = response.headers.get("client");
    const uid = response.headers.get("uid");
    const expiry = response.headers.get("expiry");
    const tokenType = response.headers.get("token-type");
    const tokenKind = response.headers.get("access-token-kind");

    const present = [accessToken, client, uid, expiry, tokenType].filter(Boolean);
    if (present.length > 0 && present.length < 5) {
        console.warn(`Partial rotation headers (${present.length}/5). Token rotation skipped.`);
    }

    if (!accessToken || !client || !uid || !expiry || !tokenType) return null;

    return { accessToken, client, uid, expiry, tokenType, ...(tokenKind ? { tokenKind } : {}) };
}

function normalizeHeaders(headers?: HeadersInit): Record<string, string> {
    if (!headers) return {};
    if (headers instanceof Headers) {
        const out: Record<string, string> = {};
        headers.forEach((v, k) => {
            out[k] = v;
        });
        return out;
    }
    if (Array.isArray(headers)) return Object.fromEntries(headers);
    return headers as Record<string, string>;
}

// ---------------------------------------------------------------------------
// OgiriAuth — central state manager
// ---------------------------------------------------------------------------

type AuthErrorCallback = (error: OgiriAuthError) => void;
type AuthChangeListener = () => void;

export interface OgiriAuthConfig {
    authMethod?: OgiriAuthMethod;
    storage?: TokenStorage;
    onAuthError?: AuthErrorCallback;
}

/**
 * Central auth primitive.
 * Manages token state and provides adapter factories for fetch, axios, ky, ofetch, etc.
 */
export class OgiriAuth {
    private readonly authMethod: OgiriAuthMethod;
    private readonly storage: TokenStorage;
    private authErrorCallback?: AuthErrorCallback;
    private readonly listeners: Set<AuthChangeListener> = new Set();

    constructor(config: OgiriAuthConfig = {}) {
        this.authMethod = config.authMethod ?? "headers";
        this.storage = config.storage ?? new MemoryTokenStorage();
        this.authErrorCallback = config.onAuthError;
    }

    getTokens(): OgiriTokens | null {
        return this.storage.get();
    }
    setTokens(tokens: OgiriTokens): void {
        this.storage.set(tokens);
        this.notify();
    }
    clearTokens(): void {
        this.storage.clear();
        this.notify();
    }
    isAuthenticated(): boolean {
        return this.storage.get() !== null;
    }

    onAuthError(cb: AuthErrorCallback): void {
        this.authErrorCallback = cb;
    }

    /** Subscribe to auth state changes. Returns unsubscribe function. */
    subscribe(listener: AuthChangeListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /** Inject auth tokens into a fetch RequestInit. */
    injectInto(config: RequestInit): RequestInit {
        const tokens = this.storage.get();
        return tokens ? injectAuth(config, tokens, this.authMethod) : config;
    }

    /** Extract rotated tokens from a fetch Response and store them. */
    extractFrom(response: Response): void {
        const rotated = extractTokens(response);
        if (rotated) {
            this.storage.set(rotated);
            this.notify();
        }
    }

    /** Handle a 401 — clears tokens and fires the error callback. */
    handleAuthError(body: unknown): OgiriAuthError {
        this.storage.clear();
        this.notify();
        const error = new OgiriAuthError("Unauthorized", 401, body);
        this.authErrorCallback?.(error);
        return error;
    }

    /**
     * Returns a function that merges auth headers into a plain headers object.
     * Use with ky, ofetch, wretch, or any BYO HTTP client.
     *
     * @example
     * const inject = auth.headerInjector()
     * const headers = inject({ "Content-Type": "application/json" })
     */
    headerInjector(): (headers: Record<string, string>) => Record<string, string> {
        return (headers) => {
            const injected = this.injectInto({ headers });
            return (injected.headers as Record<string, string>) ?? headers;
        };
    }

    private notify(): void {
        for (const l of this.listeners) l();
    }
}
