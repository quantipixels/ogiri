import { http, HttpResponse } from "msw";
import { findUser, findUserByUid, createSession, validateSession, rotateTokens, deleteSession } from "./db";

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

    // GET /api/me
    http.get("/api/me", ({ request }) => {
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
        if (!user) {
            return HttpResponse.json({ error: "User not found" }, { status: 404 });
        }

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
                id: user.id,
                username: user.username,
                email: user.email,
            },
            { status: 200, headers },
        );
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
                message: "This is demo info from the mock API",
                timestamp: new Date().toISOString(),
            },
            { status: 200, headers },
        );
    }),
];
