import type { OgiriTokens, OgiriAuthMethod } from "./types";

/**
 * Inject authentication into request config
 */
export function injectAuth(config: RequestInit, tokens: OgiriTokens, method: OgiriAuthMethod): RequestInit {
    const headers = normalizeHeaders(config.headers);

    if (method === "headers") {
        const authHeaders: Record<string, string> = {
            "access-token": tokens.accessToken,
            client: tokens.client,
            uid: tokens.uid,
            expiry: tokens.expiry,
            "token-type": tokens.tokenType,
        };

        // H4: Include access-token-kind if present
        if (tokens.tokenKind) {
            authHeaders["access-token-kind"] = tokens.tokenKind;
        }

        return {
            ...config,
            headers: {
                ...headers,
                ...authHeaders,
            },
        };
    }

    if (method === "bearer") {
        const payload: Record<string, string> = {
            "access-token": tokens.accessToken,
            client: tokens.client,
            uid: tokens.uid,
            "token-type": tokens.tokenType,
            expiry: tokens.expiry,
        };

        // H4: Include access-token-kind if present
        if (tokens.tokenKind) {
            payload["access-token-kind"] = tokens.tokenKind;
        }

        const jsonStr = JSON.stringify(payload);

        // H3: Handle non-ASCII characters in btoa
        const base64 =
            typeof Buffer !== "undefined"
                ? Buffer.from(jsonStr).toString("base64")
                : btoa(unescape(encodeURIComponent(jsonStr)));

        return {
            ...config,
            headers: {
                ...headers,
                Authorization: `Bearer ${base64}`,
            },
        };
    }

    if (method === "cookies") {
        // C4: Cookie header is forbidden in browsers, warn and use credentials only
        if (typeof window !== "undefined") {
            console.warn(
                "Cookie authentication method is not supported in browsers. " +
                    "The browser will ignore the Cookie header. " +
                    'Use "headers" or "bearer" method instead, or rely on server-set cookies with credentials: "include".',
            );
        }

        const cookieParts = [
            `access-token=${tokens.accessToken}`,
            `client=${tokens.client}`,
            `uid=${tokens.uid}`,
            `expiry=${tokens.expiry}`,
            `token-type=${tokens.tokenType}`,
        ];

        // H4: Include access-token-kind if present
        if (tokens.tokenKind) {
            cookieParts.push(`access-token-kind=${tokens.tokenKind}`);
        }

        const cookies = cookieParts.join("; ");

        return {
            ...config,
            credentials: "include",
            headers: {
                ...headers,
                Cookie: cookies,
            },
        };
    }

    // M2: Exhaustive check for unmatched method
    const _exhaustive: never = method;
    throw new Error(`Unknown auth method: ${String(_exhaustive)}`);
}

/**
 * Extract rotated tokens from response headers
 */
export function extractTokens(response: Response): OgiriTokens | null {
    const accessToken = response.headers.get("access-token");
    const client = response.headers.get("client");
    const uid = response.headers.get("uid");
    const expiry = response.headers.get("expiry");
    const tokenType = response.headers.get("token-type");
    const tokenKind = response.headers.get("access-token-kind"); // H4: Optional

    // M1: Warn on partial token headers (likely config issue)
    const presentHeaders = [accessToken, client, uid, expiry, tokenType].filter(Boolean);
    if (presentHeaders.length > 0 && presentHeaders.length < 5) {
        console.warn(
            `Partial token rotation headers detected (${presentHeaders.length}/5 present). ` +
                "This may indicate a proxy stripping headers or incomplete server response. " +
                "Token rotation will be skipped.",
        );
    }

    if (!accessToken || !client || !uid || !expiry || !tokenType) {
        return null;
    }

    const tokens: OgiriTokens = {
        accessToken,
        client,
        uid,
        expiry,
        tokenType,
    };

    // H4: Include tokenKind if present
    if (tokenKind) {
        tokens.tokenKind = tokenKind;
    }

    return tokens;
}

/**
 * Normalize headers to plain object
 */
function normalizeHeaders(headers?: HeadersInit): Record<string, string> {
    if (!headers) return {};

    if (headers instanceof Headers) {
        const normalized: Record<string, string> = {};
        headers.forEach((value, key) => {
            normalized[key] = value;
        });
        return normalized;
    }

    if (Array.isArray(headers)) {
        return Object.fromEntries(headers);
    }

    return headers as Record<string, string>;
}
