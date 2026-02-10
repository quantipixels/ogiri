import type { OgiriAuthConfig, OgiriAuthMethod, OgiriTokens, TokenStorage } from "./types";
import { MemoryTokenStorage } from "./token-storage";
import { injectAuth, extractTokens } from "./interceptors";
import { OgiriAuthError } from "./errors";
import { OgiriFetchClient } from "./fetch-client";

type AuthErrorCallback = (error: OgiriAuthError) => void;
type AuthChangeListener = () => void;

/**
 * Central auth primitive — manages token state and provides adapter factories.
 * Zero runtime dependencies. Consumers bring their own HTTP client.
 */
export class OgiriAuth {
    private readonly authMethod: OgiriAuthMethod;
    private readonly storage: TokenStorage;
    private authErrorCallback?: AuthErrorCallback;
    private listeners: Set<AuthChangeListener> = new Set();

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
        this.notifyListeners();
    }

    clearTokens(): void {
        this.storage.clear();
        this.notifyListeners();
    }

    isAuthenticated(): boolean {
        return this.storage.get() !== null;
    }

    onAuthError(callback: AuthErrorCallback): void {
        this.authErrorCallback = callback;
    }

    /**
     * Subscribe to auth state changes (token set/clear).
     * Returns unsubscribe function.
     */
    subscribe(listener: AuthChangeListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Inject auth tokens into a fetch RequestInit config.
     */
    injectInto(config: RequestInit): RequestInit {
        const tokens = this.storage.get();
        if (!tokens) return config;
        return injectAuth(config, tokens, this.authMethod);
    }

    /**
     * Extract rotated tokens from a fetch Response and store them.
     */
    extractFrom(response: Response): void {
        const rotated = extractTokens(response);
        if (rotated) {
            this.storage.set(rotated);
            this.notifyListeners();
        }
    }

    /**
     * Handle a 401 response — clears tokens and fires the error callback.
     */
    handleAuthError(body: unknown): OgiriAuthError {
        this.storage.clear();
        this.notifyListeners();
        const error = new OgiriAuthError("Unauthorized", 401, body);
        this.authErrorCallback?.(error);
        return error;
    }

    /**
     * Returns a function that injects auth headers into a plain headers object.
     * Useful for BYO HTTP clients (ky, ofetch, etc.).
     */
    headerInjector(): (headers: Record<string, string>) => Record<string, string> {
        return (headers: Record<string, string>) => {
            const injected = this.injectInto({ headers });
            return (injected.headers as Record<string, string>) ?? headers;
        };
    }

    /**
     * Factory for the convenience fetch client.
     */
    createFetchClient(baseURL: string): OgiriFetchClient {
        return new OgiriFetchClient(this, baseURL);
    }

    private notifyListeners(): void {
        for (const listener of this.listeners) {
            listener();
        }
    }
}
