/**
 * Ogiri axios interceptors — copy this alongside auth.ts into your project.
 *
 * Wires OgiriAuth into an axios instance: injects auth headers on every request,
 * extracts rotated tokens from every response, and handles 401s.
 *
 * Usage:
 *   const { request, response } = createAxiosInterceptors(auth)
 *   api.interceptors.request.use(request)
 *   api.interceptors.response.use(response.onFulfilled, response.onRejected)
 */
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from "axios";
import type { OgiriAuth } from "./auth";

export interface OgiriAxiosInterceptors {
    request: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig;
    response: {
        onFulfilled: (response: AxiosResponse) => AxiosResponse;
        onRejected: (error: AxiosError) => Promise<never>;
    };
}

/** Ogiri header names carried in every authenticated response. */
const TOKEN_HEADER_NAMES = ["access-token", "client", "uid", "expiry", "token-type", "access-token-kind"] as const;

export function createAxiosInterceptors(auth: OgiriAuth): OgiriAxiosInterceptors {
    return {
        request: (config) => {
            const tokens = auth.getTokens();
            if (tokens) {
                const injected = auth.injectInto({ headers: {} });
                const headers = injected.headers as Record<string, string> | undefined;
                if (headers) {
                    for (const [key, value] of Object.entries(headers)) {
                        config.headers.set(key, value);
                    }
                }
            }
            return config;
        },

        response: {
            onFulfilled: (response) => {
                // axios uses a plain object for headers, not the Fetch API Headers class.
                // Build a minimal Response-like object so auth.extractFrom() can read them.
                const headerMap: Record<string, string> = {};
                for (const name of TOKEN_HEADER_NAMES) {
                    const value = response.headers[name];
                    if (typeof value === "string") headerMap[name] = value;
                }
                auth.extractFrom(new Response(null, { headers: new Headers(headerMap) }));
                return response;
            },

            onRejected: async (error) => {
                if (error.response?.status === 401) {
                    throw auth.handleAuthError(error.response.data);
                }
                throw error;
            },
        },
    };
}
