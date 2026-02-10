import { describe, it, expect, beforeEach, vi } from "vitest";
import { MemoryTokenStorage, LocalStorageTokenStorage } from "../src/token-storage";
import type { OgiriTokens } from "../src/types";

const mockTokens: OgiriTokens = {
    accessToken: "test-token",
    client: "test-client",
    uid: "user@example.com",
    expiry: "1234567890",
    tokenType: "Bearer",
};

describe("MemoryTokenStorage", () => {
    let storage: MemoryTokenStorage;

    beforeEach(() => {
        storage = new MemoryTokenStorage();
    });

    it("should return null when no tokens are stored", () => {
        expect(storage.get()).toBeNull();
    });

    it("should store and retrieve tokens", () => {
        storage.set(mockTokens);
        expect(storage.get()).toEqual(mockTokens);
    });

    it("should clear tokens", () => {
        storage.set(mockTokens);
        storage.clear();
        expect(storage.get()).toBeNull();
    });

    it("should overwrite existing tokens", () => {
        storage.set(mockTokens);
        const newTokens: OgiriTokens = { ...mockTokens, accessToken: "new-token" };
        storage.set(newTokens);
        expect(storage.get()).toEqual(newTokens);
    });
});

describe("LocalStorageTokenStorage", () => {
    let storage: LocalStorageTokenStorage;
    const storageKey = "test-ogiri-tokens";

    beforeEach(() => {
        vi.stubGlobal("localStorage", {
            getItem: vi.fn(),
            setItem: vi.fn(),
            removeItem: vi.fn(),
        });
        storage = new LocalStorageTokenStorage(storageKey);
    });

    it("should return null when localStorage has no tokens", () => {
        vi.mocked(localStorage.getItem).mockReturnValue(null);
        expect(storage.get()).toBeNull();
    });

    it("should return null when localStorage has invalid JSON", () => {
        vi.mocked(localStorage.getItem).mockReturnValue("invalid-json");
        expect(storage.get()).toBeNull();
    });

    it("should retrieve tokens from localStorage", () => {
        vi.mocked(localStorage.getItem).mockReturnValue(JSON.stringify(mockTokens));
        expect(storage.get()).toEqual(mockTokens);
        expect(localStorage.getItem).toHaveBeenCalledWith(storageKey);
    });

    it("should store tokens to localStorage", () => {
        storage.set(mockTokens);
        expect(localStorage.setItem).toHaveBeenCalledWith(storageKey, JSON.stringify(mockTokens));
    });

    it("should clear tokens from localStorage", () => {
        storage.clear();
        expect(localStorage.removeItem).toHaveBeenCalledWith(storageKey);
    });
});
