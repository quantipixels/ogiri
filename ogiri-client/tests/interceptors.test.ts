import { describe, it, expect } from "vitest";
import { injectAuth, extractTokens } from "../src/interceptors";
import type { OgiriTokens } from "../src/types";

const mockTokens: OgiriTokens = {
    accessToken: "test-token",
    client: "test-client",
    uid: "user@example.com",
    expiry: "1234567890",
    tokenType: "Bearer",
};

describe("injectAuth", () => {
    it("should inject headers auth method", () => {
        const config: RequestInit = {};
        const result = injectAuth(config, mockTokens, "headers");

        expect(result.headers).toEqual({
            "access-token": "test-token",
            client: "test-client",
            uid: "user@example.com",
            expiry: "1234567890",
            "token-type": "Bearer",
        });
    });

    it("should preserve existing headers with headers auth", () => {
        const config: RequestInit = {
            headers: { "Content-Type": "application/json" },
        };
        const result = injectAuth(config, mockTokens, "headers");

        expect(result.headers).toEqual({
            "Content-Type": "application/json",
            "access-token": "test-token",
            client: "test-client",
            uid: "user@example.com",
            expiry: "1234567890",
            "token-type": "Bearer",
        });
    });

    it("should inject bearer auth method", () => {
        const config: RequestInit = {};
        const result = injectAuth(config, mockTokens, "bearer");

        const authHeader = (result.headers as Record<string, string>)?.Authorization;
        expect(authHeader).toMatch(/^Bearer /);

        const base64 = authHeader!.replace("Bearer ", "");
        const decoded = JSON.parse(
            typeof Buffer !== "undefined" ? Buffer.from(base64, "base64").toString("utf-8") : atob(base64),
        );

        expect(decoded).toEqual({
            "access-token": "test-token",
            client: "test-client",
            uid: "user@example.com",
            "token-type": "Bearer",
            expiry: "1234567890",
        });
    });

    it("should inject cookies auth method", () => {
        const config: RequestInit = {};
        const result = injectAuth(config, mockTokens, "cookies");

        expect(result.credentials).toBe("include");
        const cookieHeader = (result.headers as Record<string, string>)?.Cookie;
        expect(cookieHeader).toContain("access-token=test-token");
        expect(cookieHeader).toContain("client=test-client");
        expect(cookieHeader).toContain("uid=user@example.com");
        expect(cookieHeader).toContain("expiry=1234567890");
        expect(cookieHeader).toContain("token-type=Bearer");
    });

    it("should handle Headers object", () => {
        const config: RequestInit = {
            headers: new Headers({ "X-Custom": "value" }),
        };
        const result = injectAuth(config, mockTokens, "headers");

        const headers = result.headers as Record<string, string>;
        // Headers API normalizes names to lowercase
        expect(headers["x-custom"]).toBe("value");
        expect(headers["access-token"]).toBe("test-token");
    });
});

describe("extractTokens", () => {
    it("should extract tokens from response headers", () => {
        const headers = new Headers({
            "access-token": "new-token",
            client: "new-client",
            uid: "newuser@example.com",
            expiry: "9876543210",
            "token-type": "Bearer",
        });

        const response = new Response(null, { headers });
        const tokens = extractTokens(response);

        expect(tokens).toEqual({
            accessToken: "new-token",
            client: "new-client",
            uid: "newuser@example.com",
            expiry: "9876543210",
            tokenType: "Bearer",
        });
    });

    it("should return null when headers are missing", () => {
        const headers = new Headers({
            "access-token": "token",
            client: "client",
        });

        const response = new Response(null, { headers });
        const tokens = extractTokens(response);

        expect(tokens).toBeNull();
    });

    it("should return null when no auth headers present", () => {
        const response = new Response(null);
        const tokens = extractTokens(response);

        expect(tokens).toBeNull();
    });

    it("should handle case-insensitive headers", () => {
        const headers = new Headers({
            "Access-Token": "new-token",
            Client: "new-client",
            Uid: "user@example.com",
            Expiry: "9876543210",
            "Token-Type": "Bearer",
        });

        const response = new Response(null, { headers });
        const tokens = extractTokens(response);

        expect(tokens).toEqual({
            accessToken: "new-token",
            client: "new-client",
            uid: "user@example.com",
            expiry: "9876543210",
            tokenType: "Bearer",
        });
    });
});
