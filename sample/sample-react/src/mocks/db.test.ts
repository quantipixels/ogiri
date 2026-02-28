import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { findUser, findUserByUid, createSession, validateSession, rotateTokens, deleteSession } from "./db";

const USER = { username: "user1", password: "password" };

describe("createSession", () => {
    it("returns all required token fields", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        expect(tokens["access-token"]).toBeTruthy();
        expect(tokens.client).toBeTruthy();
        expect(tokens.uid).toBe(user.id.toString());
        expect(tokens["token-type"]).toBe("Bearer");
        expect(new Date(tokens.expiry).getTime()).toBeGreaterThan(Date.now());
    });

    it("expiry is ~1 minute from now", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);
        const ttl = new Date(tokens.expiry).getTime() - Date.now();

        expect(ttl).toBeGreaterThan(55_000);
        expect(ttl).toBeLessThan(65_000);
    });

    it("each session gets a unique client ID", () => {
        const user = findUser(USER.username, USER.password)!;
        const a = createSession(user);
        const b = createSession(user);
        expect(a.client).not.toBe(b.client);
    });
});

describe("validateSession", () => {
    it("accepts a valid token", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);
        expect(validateSession(tokens.client, tokens["access-token"])).toBeDefined();
    });

    it("rejects an unknown client", () => {
        expect(validateSession("no-such-client", "any-token")).toBeUndefined();
    });

    it("rejects a wrong access token", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);
        expect(validateSession(tokens.client, "wrong-token")).toBeUndefined();
    });

    it("rejects an expired session", () => {
        vi.useFakeTimers();
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        vi.advanceTimersByTime(61_000); // past the 1-minute expiry

        expect(validateSession(tokens.client, tokens["access-token"])).toBeUndefined();
        vi.useRealTimers();
    });
});

describe("rotateTokens", () => {
    afterEach(() => vi.useRealTimers());

    it("does not rotate immediately after session creation", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);
        expect(rotateTokens(tokens.client)).toBeNull();
    });

    it("rotates after 30 seconds", () => {
        vi.useFakeTimers();
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        vi.advanceTimersByTime(31_000);
        const rotated = rotateTokens(tokens.client);

        expect(rotated).not.toBeNull();
        expect(rotated!["access-token"]).not.toBe(tokens["access-token"]);
        expect(rotated!.client).toBe(tokens.client); // client ID unchanged
        expect(rotated!.uid).toBe(tokens.uid);
    });

    it("does not rotate again before 30 seconds after the last rotation", () => {
        vi.useFakeTimers();
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        vi.advanceTimersByTime(31_000);
        const first = rotateTokens(tokens.client);
        expect(first).not.toBeNull();

        vi.advanceTimersByTime(10_000); // only 10 s since last rotation
        expect(rotateTokens(tokens.client)).toBeNull();
    });

    it("rotated token passes validateSession", () => {
        vi.useFakeTimers();
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        vi.advanceTimersByTime(31_000);
        const rotated = rotateTokens(tokens.client)!;

        expect(validateSession(rotated.client, rotated["access-token"])).toBeDefined();
    });

    it("old token is rejected after rotation", () => {
        vi.useFakeTimers();
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        vi.advanceTimersByTime(31_000);
        rotateTokens(tokens.client);

        expect(validateSession(tokens.client, tokens["access-token"])).toBeUndefined();
    });

    it("returns null for an unknown client", () => {
        expect(rotateTokens("no-such-client")).toBeNull();
    });
});

describe("deleteSession", () => {
    it("invalidates the session", () => {
        const user = findUser(USER.username, USER.password)!;
        const tokens = createSession(user);

        deleteSession(tokens.client);

        expect(validateSession(tokens.client, tokens["access-token"])).toBeUndefined();
    });
});

describe("findUser / findUserByUid", () => {
    it("finds a user by credentials", () => {
        expect(findUser("user1", "password")).toBeDefined();
    });

    it("returns undefined for wrong password", () => {
        expect(findUser("user1", "wrong")).toBeUndefined();
    });

    it("finds a user by uid", () => {
        const user = findUser("user1", "password")!;
        expect(findUserByUid(user.id.toString())).toEqual(user);
    });
});
