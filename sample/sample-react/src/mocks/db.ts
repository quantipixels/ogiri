export interface MockUser {
    id: number;
    username: string;
    password: string;
    email: string;
}

export interface MockSession {
    token: string;
    client: string;
    uid: string;
    expiry: string;
    lastRequestAt: number;
}

// Seed users
const users: MockUser[] = [
    { id: 1, username: "user1", password: "password", email: "user1@example.com" },
    { id: 2, username: "user2", password: "password", email: "user2@example.com" },
];

// Session storage keyed by client ID
const sessions = new Map<string, MockSession>();

export function findUser(username: string, password: string): MockUser | undefined {
    return users.find((u) => u.username === username && u.password === password);
}

export function findUserByUid(uid: string): MockUser | undefined {
    return users.find((u) => u.id.toString() === uid);
}

function generateToken(): string {
    return Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
}

export function createSession(user: MockUser): {
    "access-token": string;
    client: string;
    uid: string;
    expiry: string;
    "token-type": string;
} {
    const token = generateToken();
    const client = generateToken();
    const uid = user.id.toString();
    const expiry = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(); // 7 days
    const tokenType = "Bearer";

    sessions.set(client, {
        token,
        client,
        uid,
        expiry,
        lastRequestAt: Date.now(),
    });

    return {
        "access-token": token,
        client,
        uid,
        expiry,
        "token-type": tokenType,
    };
}

export function validateSession(clientId: string, accessToken: string): MockSession | undefined {
    const session = sessions.get(clientId);
    if (!session || session.token !== accessToken) {
        return undefined;
    }
    if (new Date(session.expiry) < new Date()) {
        sessions.delete(clientId);
        return undefined;
    }
    return session;
}

export function rotateTokens(clientId: string): {
    "access-token": string;
    client: string;
    uid: string;
    expiry: string;
    "token-type": string;
} | null {
    const session = sessions.get(clientId);
    if (!session) {
        return null;
    }

    // Skip rotation if last request was < 30s ago
    const now = Date.now();
    if (now - session.lastRequestAt < 30000) {
        return null;
    }

    // Generate new token, keep same client ID
    const newToken = generateToken();
    session.token = newToken;
    session.lastRequestAt = now;
    sessions.set(clientId, session);

    return {
        "access-token": newToken,
        client: session.client,
        uid: session.uid,
        expiry: session.expiry,
        "token-type": "Bearer",
    };
}

export function deleteSession(clientId: string): void {
    sessions.delete(clientId);
}
