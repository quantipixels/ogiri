import { describe, it, expect, beforeEach, vi } from "vitest";
import { OgiriAuth } from "../src/auth";
import { OgiriAuthError } from "../src/errors";
import { createAxiosInterceptors } from "../src/axios/interceptors";
import type { OgiriTokens } from "../src/types";
import type { AxiosError, AxiosHeaders, AxiosResponse, InternalAxiosRequestConfig } from "axios";

const mockTokens: OgiriTokens = {
    accessToken: "test-token",
    client: "test-client",
    uid: "user@example.com",
    expiry: "1234567890",
    tokenType: "Bearer",
};

function makeAxiosConfig(): InternalAxiosRequestConfig {
    const headerMap = new Map<string, string>();
    const headers = {
        set(key: string, value: string) {
            headerMap.set(key, value);
        },
        get(key: string) {
            return headerMap.get(key);
        },
        _map: headerMap,
    } as unknown as AxiosHeaders;
    return { headers } as InternalAxiosRequestConfig;
}

function makeAxiosResponse(responseHeaders: Record<string, string> = {}, status = 200): AxiosResponse {
    return {
        data: {},
        status,
        statusText: "OK",
        headers: responseHeaders,
        config: { headers: {} as AxiosHeaders },
    } as AxiosResponse;
}

function makeAxiosError(status: number, data: unknown = null): AxiosError {
    return {
        response: {
            status,
            data,
            headers: {},
            statusText: "Unauthorized",
            config: { headers: {} as AxiosHeaders },
        },
        isAxiosError: true,
        name: "AxiosError",
        message: "Request failed",
        config: { headers: {} as AxiosHeaders },
        toJSON: () => ({}),
    } as AxiosError;
}

describe("createAxiosInterceptors", () => {
    let auth: OgiriAuth;

    beforeEach(() => {
        auth = new OgiriAuth({ authMethod: "headers" });
    });

    describe("request interceptor", () => {
        it("should inject auth headers when authenticated", () => {
            auth.setTokens(mockTokens);
            const { request } = createAxiosInterceptors(auth);

            const config = makeAxiosConfig();
            const result = request(config);

            const headerMap = (result.headers as any)._map as Map<string, string>;
            expect(headerMap.get("access-token")).toBe("test-token");
            expect(headerMap.get("client")).toBe("test-client");
            expect(headerMap.get("uid")).toBe("user@example.com");
        });

        it("should not inject headers when unauthenticated", () => {
            const { request } = createAxiosInterceptors(auth);

            const config = makeAxiosConfig();
            const result = request(config);

            const headerMap = (result.headers as any)._map as Map<string, string>;
            expect(headerMap.size).toBe(0);
        });
    });

    describe("response interceptor", () => {
        it("should extract rotated tokens from response", () => {
            const { response } = createAxiosInterceptors(auth);

            const axiosResponse = makeAxiosResponse({
                "access-token": "rotated-token",
                client: "rotated-client",
                uid: "user@example.com",
                expiry: "9999999999",
                "token-type": "Bearer",
            });

            response.onFulfilled(axiosResponse);

            expect(auth.getTokens()).toEqual({
                accessToken: "rotated-token",
                client: "rotated-client",
                uid: "user@example.com",
                expiry: "9999999999",
                tokenType: "Bearer",
            });
        });

        it("should not update tokens when rotation headers missing", () => {
            auth.setTokens(mockTokens);
            const { response } = createAxiosInterceptors(auth);

            const axiosResponse = makeAxiosResponse();
            response.onFulfilled(axiosResponse);

            expect(auth.getTokens()).toEqual(mockTokens);
        });
    });

    describe("error interceptor", () => {
        it("should handle 401 by clearing tokens and throwing OgiriAuthError", async () => {
            auth.setTokens(mockTokens);
            const { response } = createAxiosInterceptors(auth);

            const error = makeAxiosError(401, { error: "Unauthorized" });

            await expect(response.onRejected(error)).rejects.toThrow(OgiriAuthError);
            expect(auth.isAuthenticated()).toBe(false);
        });

        it("should fire onAuthError callback on 401", async () => {
            const onAuthError = vi.fn();
            auth = new OgiriAuth({ authMethod: "headers", onAuthError });
            auth.setTokens(mockTokens);
            const { response } = createAxiosInterceptors(auth);

            const error = makeAxiosError(401);

            await expect(response.onRejected(error)).rejects.toThrow(OgiriAuthError);
            expect(onAuthError).toHaveBeenCalled();
        });

        it("should re-throw non-401 errors unchanged", async () => {
            const { response } = createAxiosInterceptors(auth);

            const error = makeAxiosError(500);

            await expect(response.onRejected(error)).rejects.toBe(error);
        });
    });
});
