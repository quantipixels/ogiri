import { http, HttpResponse } from "msw";
import {
    findUser,
    findUserByUid,
    createSession,
    validateSession,
    rotateTokens,
    deleteSession,
    expireSession,
} from "./db";

export const handlers = [
    // POST /api/auth/login
    http.post("/api/auth/login", async ({ request }) => {
        const body = (await request.json()) as { username?: string; password?: string };

        if (!body.username || !body.password) {
            return HttpResponse.json({ error: "Missing username or password" }, { status: 400 });
        }

        const user = findUser(body.username, body.password);
        if (!user) {
            return HttpResponse.json({ error: "Invalid credentials" }, { status: 401 });
        }

        const tokens = createSession(user);

        return HttpResponse.json(tokens, {
            status: 200,
            headers: {
                "access-token": tokens["access-token"],
                client: tokens.client,
                uid: tokens.uid,
                expiry: tokens.expiry,
                "token-type": tokens["token-type"],
            },
        });
    }),

    // POST /api/auth/logout
    http.post("/api/auth/logout", ({ request }) => {
        const accessToken = request.headers.get("access-token");
        const client = request.headers.get("client");

        if (!client || !accessToken) {
            return HttpResponse.json({ error: "Missing auth headers" }, { status: 400 });
        }

        deleteSession(client);
        return HttpResponse.json({ message: "Logged out" }, { status: 200 });
    }),

    // POST /api/test/expire-token
    http.post("/api/test/expire-token", ({ request }) => {
        const client = request.headers.get("client");
        if (!client) {
            return HttpResponse.json({ error: "Missing client header" }, { status: 400 });
        }
        const expired = expireSession(client);
        if (!expired) {
            return HttpResponse.json({ error: "Session not found" }, { status: 404 });
        }
        return HttpResponse.json({ message: "Token expired" }, { status: 200 });
    }),

    // GET /api/demo/info
    http.get("/api/demo/info", ({ request }) => {
        const accessToken = request.headers.get("access-token");
        const client = request.headers.get("client");

        if (!client || !accessToken) {
            return HttpResponse.json({ error: "Unauthorized" }, { status: 401 });
        }

        const session = validateSession(client, accessToken);
        if (!session) {
            return HttpResponse.json({ error: "Unauthorized" }, { status: 401 });
        }

        const user = findUserByUid(session.uid);

        const rotated = rotateTokens(client);
        const headers: Record<string, string> = {};
        if (rotated) {
            headers["access-token"] = rotated["access-token"];
            headers.client = rotated.client;
            headers.uid = rotated.uid;
            headers.expiry = rotated.expiry;
            headers["token-type"] = rotated["token-type"];
        }

        return HttpResponse.json(
            {
                authenticated: true,
                principal: user?.username ?? session.uid,
                authorities: ["ROLE_USER"],
                authMethod: "Header",
                message: "This endpoint accepts authentication via headers, cookies, or Bearer token",
            },
            { status: 200, headers },
        );
    }),
];
