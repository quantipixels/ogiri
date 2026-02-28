/**
 * OgiriAuth live integration tests — React sample.
 *
 * Runs against a real Spring Boot server (Java or Kotlin sample app).
 * Skipped unless OGIRI_BASE_URL is set.
 *
 * Start the server and run these tests via:
 *   ./run-live.sh [java|kotlin]
 *
 * Or manually:
 *   OGIRI_BASE_URL=http://localhost:48081 pnpm test:live
 */
import { beforeEach, describe, expect, it } from "vitest";
import axios from "axios";
import { MemoryTokenStorage, OgiriAuth, OgiriAuthError } from "./auth";
import { createAxiosInterceptors } from "./axios-ogiri";

const BASE_URL = process.env.OGIRI_BASE_URL;

describe.skipIf(!BASE_URL)("OgiriAuth — live integration", () => {
    let auth: OgiriAuth;
    let api: ReturnType<typeof axios.create>;

    beforeEach(() => {
        auth = new OgiriAuth({ authMethod: "headers", storage: new MemoryTokenStorage() });
        api = axios.create({ baseURL: BASE_URL });
        const { request, response } = createAxiosInterceptors(auth);
        api.interceptors.request.use(request);
        api.interceptors.response.use(response.onFulfilled, response.onRejected);
    });

    it("login extracts all token headers into storage", async () => {
        expect(auth.getTokens()).toBeNull();

        await api.post("/api/auth/login", { username: "user1@example.com", password: "password" });

        const tokens = auth.getTokens();
        expect(tokens?.accessToken).toBeTruthy();
        expect(tokens?.client).toBeTruthy();
        expect(tokens?.uid).toBeTruthy();
        expect(tokens?.expiry).toBeTruthy();
        expect(tokens?.tokenType).toBe("Bearer");
    });

    it("invalid credentials return OgiriAuthError", async () => {
        await expect(
            api.post("/api/auth/login", { username: "user1@example.com", password: "wrong" }),
        ).rejects.toBeInstanceOf(OgiriAuthError);
    });

    it("authenticated request injects headers and interceptor extracts rotated token", async () => {
        // ── 1. Login ──────────────────────────────────────────────────────────
        await api.post("/api/auth/login", { username: "user1@example.com", password: "password" });
        const loginToken = auth.getTokens()!.accessToken;
        expect(loginToken).toBeTruthy();

        // ── 2. Authenticated request ──────────────────────────────────────────
        // - Request interceptor injects stored tokens as headers
        // - Server receives auth headers → authenticates → returns 200
        // - Server rotates token (rotate-stale-seconds=0 on the test server)
        // - Response interceptor extracts rotation headers → updates auth storage
        const r = await api.get("/api/demo/info");
        expect(r.data.authenticated).toBe(true);
        expect(r.data.authMethod).toBe("Header");

        const rotatedToken = auth.getTokens()!.accessToken;
        expect(rotatedToken).not.toBe(loginToken);
    });
});
