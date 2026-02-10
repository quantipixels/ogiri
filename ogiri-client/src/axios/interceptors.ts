import type { OgiriAuth } from "../auth";
import type { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from "axios";

export interface OgiriAxiosInterceptors {
    request: (config: InternalAxiosRequestConfig) => InternalAxiosRequestConfig;
    response: {
        onFulfilled: (response: AxiosResponse) => AxiosResponse;
        onRejected: (error: AxiosError) => Promise<never>;
    };
}

/**
 * Create axios interceptors that wire OgiriAuth into an axios instance.
 * Request interceptor injects auth headers; response interceptor extracts
 * rotated tokens and handles 401 errors.
 */
export function createAxiosInterceptors(auth: OgiriAuth): OgiriAxiosInterceptors {
    return {
        request: (config: InternalAxiosRequestConfig) => {
            const tokens = auth.getTokens();
            if (tokens) {
                const injected = auth.injectInto({ headers: {} });
                const injectedHeaders = injected.headers as Record<string, string> | undefined;
                if (injectedHeaders) {
                    for (const [key, value] of Object.entries(injectedHeaders)) {
                        config.headers.set(key, value);
                    }
                }
            }
            return config;
        },

        response: {
            onFulfilled: (response: AxiosResponse) => {
                const headerMap: Record<string, string> = {};
                const headerNames = ["access-token", "client", "uid", "expiry", "token-type", "access-token-kind"];
                for (const name of headerNames) {
                    const value = response.headers[name];
                    if (typeof value === "string") {
                        headerMap[name] = value;
                    }
                }

                // Build a minimal Response-like object for extractFrom
                const fakeResponse = new Response(null, {
                    headers: new Headers(headerMap),
                });
                auth.extractFrom(fakeResponse);

                return response;
            },

            onRejected: async (error: AxiosError) => {
                if (error.response?.status === 401) {
                    const body = error.response.data;
                    throw auth.handleAuthError(body);
                }
                throw error;
            },
        },
    };
}
