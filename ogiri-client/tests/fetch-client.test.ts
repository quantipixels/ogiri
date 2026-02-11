import { describe, it, expect, beforeEach, vi } from "vitest";
import { OgiriAuth } from "../src/auth";
import { OgiriFetchClient } from "../src/fetch-client";
import { OgiriAuthError } from "../src/errors";
import type { OgiriTokens } from "../src/types";

const mockTokens: OgiriTokens = {
    accessToken: "test-token",
    client: "test-client",
    uid: "user@example.com",
    expiry: "1234567890",
    tokenType: "Bearer",
};

describe("OgiriFetchClient", () => {
    let auth: OgiriAuth;
    let client: OgiriFetchClient;
    let fetchMock: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);
        auth = new OgiriAuth();
        client = new OgiriFetchClient(auth, "https://api.example.com");
    });

    describe("request", () => {
        it("should make unauthenticated request", async () => {
            const mockResponse = { data: "test" };
            fetchMock.mockResolvedValue(
                new Response(JSON.stringify(mockResponse), {
                    status: 200,
                    headers: { "Content-Type": "application/json" },
                }),
            );

            const result = await client.request("/test");

            expect(fetchMock).toHaveBeenCalledWith(
                "https://api.example.com/test",
                expect.objectContaining({ method: "GET" }),
            );
            expect(result.data).toEqual(mockResponse);
        });

        it("should inject auth headers when authenticated", async () => {
            auth.setTokens(mockTokens);
            fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));

            await client.request("/protected");

            const callArgs = fetchMock.mock.calls[0];
            const headers = callArgs[1].headers;

            expect(headers["access-token"]).toBe("test-token");
            expect(headers.client).toBe("test-client");
            expect(headers.uid).toBe("user@example.com");
        });

        it("should extract and store rotated tokens from response", async () => {
            auth.setTokens(mockTokens);

            fetchMock.mockResolvedValue(
                new Response("{}", {
                    status: 200,
                    headers: {
                        "access-token": "rotated-token",
                        client: "rotated-client",
                        uid: "user@example.com",
                        expiry: "9999999999",
                        "token-type": "Bearer",
                    },
                }),
            );

            await client.request("/test");

            expect(auth.getTokens()).toEqual({
                accessToken: "rotated-token",
                client: "rotated-client",
                uid: "user@example.com",
                expiry: "9999999999",
                tokenType: "Bearer",
            });
        });

        it("should send JSON body", async () => {
            fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));

            await client.request("/test", {
                method: "POST",
                body: { foo: "bar" },
            });

            const callArgs = fetchMock.mock.calls[0];
            expect(callArgs[1].body).toBe('{"foo":"bar"}');
            expect(callArgs[1].headers["Content-Type"]).toBe("application/json");
        });

        it("should handle query params", async () => {
            fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));

            await client.request("/test", {
                params: { foo: "bar", baz: "qux" },
            });

            const callArgs = fetchMock.mock.calls[0];
            expect(callArgs[0]).toBe("https://api.example.com/test?foo=bar&baz=qux");
        });

        it("should throw OgiriAuthError on 401", async () => {
            auth.setTokens(mockTokens);

            fetchMock.mockResolvedValue(new Response('{"error":"Unauthorized"}', { status: 401 }));

            await expect(client.request("/protected")).rejects.toThrow(OgiriAuthError);
            expect(auth.isAuthenticated()).toBe(false);
        });

        it("should fire onAuthError callback on 401", async () => {
            const onAuthError = vi.fn();
            auth = new OgiriAuth({ onAuthError });
            client = new OgiriFetchClient(auth, "https://api.example.com");
            auth.setTokens(mockTokens);

            fetchMock.mockResolvedValue(new Response('{"error":"Unauthorized"}', { status: 401 }));

            await expect(client.request("/protected")).rejects.toThrow(OgiriAuthError);
            expect(onAuthError).toHaveBeenCalled();
        });

        it("should throw generic error on non-401 failures", async () => {
            fetchMock.mockResolvedValue(new Response("Internal Server Error", { status: 500 }));

            await expect(client.request("/test")).rejects.toThrow("HTTP 500");
        });
    });

    describe("convenience methods", () => {
        beforeEach(() => {
            fetchMock.mockResolvedValue(new Response("{}", { status: 200 }));
        });

        it("should call POST with serialized JSON body", async () => {
            await client.post("/users", { name: "John" });
            const callArgs = fetchMock.mock.calls[0];
            expect(callArgs[1].method).toBe("POST");
            expect(callArgs[1].body).toBe('{"name":"John"}');
            expect(callArgs[0]).toBe("https://api.example.com/users");
        });
    });
});
