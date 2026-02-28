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
        (cb) => auth.subscribe(cb),
        () => auth.getTokens(),
    );

    const isAuthenticated = tokens !== null;

    const login = useCallback((t: OgiriTokens) => auth.setTokens(t), []);
    const logout = useCallback(() => auth.clearTokens(), []);

    const value = useMemo(() => ({ isAuthenticated, tokens, login, logout }), [isAuthenticated, tokens, login, logout]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
