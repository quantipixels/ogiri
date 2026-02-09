# Ògiri TypeScript Client

TypeScript/JavaScript client for Ògiri opaque token authentication with automatic token rotation.

## Status

**⚠️ In Development (v0.1.0)** - Not yet published to npm. API subject to change.

## Features

- **Automatic token rotation** - Handles token refresh transparently
- **Multiple auth methods** - Headers, cookies, or both
- **Storage backends** - Memory, localStorage, or custom
- **Type-safe** - Full TypeScript support
- **Error handling** - Robust error handling with detailed context (see [lessons learned](../AGENTS.md#lessons-learned-2026-02-06))

## Installation

```bash
# Not yet published - install from local directory
pnpm add ./ogiri-client
```

## Quick Start

### Basic Usage

```typescript
import { OgiriClient } from "ogiri-security-client";

const client = new OgiriClient({
  baseURL: "https://api.example.com",
  authMethod: "headers", // 'headers' | 'cookies' | 'both'
});

// Login
const loginResponse = await client.post("/api/auth/login", {
  body: { username: "user", password: "pass" },
});

// Authenticated requests (tokens injected automatically)
const data = await client.get("/api/protected/resource");

// Logout
await client.post("/api/auth/logout");
```

### Custom Storage

```typescript
import { OgiriClient, TokenStorage } from "ogiri-security-client";

class CustomStorage implements TokenStorage {
  async save(tokens: OgiriTokens): Promise<void> {
    // Save to your preferred storage
  }

  async load(): Promise<OgiriTokens | null> {
    // Load from your storage
  }

  async clear(): Promise<void> {
    // Clear storage
  }
}

const client = new OgiriClient({
  baseURL: "https://api.example.com",
  storage: new CustomStorage(),
});
```

### Error Handling

```typescript
import { OgiriClient, OgiriAuthError } from "ogiri-security-client";

const client = new OgiriClient({
  baseURL: "https://api.example.com",
  onAuthError: (error: OgiriAuthError) => {
    console.error("Auth failed:", error.message);
    // Redirect to login, show notification, etc.
  },
});

try {
  await client.get("/api/protected");
} catch (error) {
  if (error instanceof OgiriAuthError) {
    // Handle authentication errors
  }
}
```

## API Reference

### OgiriClient

#### Constructor Options

```typescript
interface OgiriClientConfig {
  baseURL: string; // API base URL
  authMethod?: "headers" | "cookies" | "both"; // Default: 'headers'
  storage?: TokenStorage; // Default: MemoryTokenStorage
  onAuthError?: (error: OgiriAuthError) => void;
}
```

#### Methods

- `get<T>(path, options?)` - GET request
- `post<T>(path, options?)` - POST request
- `put<T>(path, options?)` - PUT request
- `patch<T>(path, options?)` - PATCH request
- `delete<T>(path, options?)` - DELETE request
- `request<T>(path, options)` - Generic request

#### Request Options

```typescript
interface OgiriRequestOptions {
  params?: Record<string, string>; // Query parameters
  body?: unknown; // Request body (auto-serialized to JSON)
  headers?: Record<string, string>; // Additional headers
  // ...standard fetch options
}
```

### TokenStorage Interface

Implement this interface for custom token storage:

```typescript
interface TokenStorage {
  save(tokens: OgiriTokens): Promise<void>;
  load(): Promise<OgiriTokens | null>;
  clear(): Promise<void>;
}
```

### Built-in Storage

- **MemoryTokenStorage** - In-memory storage (default, lost on reload)
- **LocalStorageTokenStorage** - Browser localStorage (persistent)

## Development

```bash
pnpm install          # Install dependencies
pnpm build            # Build for production
pnpm test             # Run tests
pnpm test:watch       # Watch mode
pnpm typecheck        # Type checking
```

## Security Considerations

This client implements security best practices identified in code review:

- **Type safety at boundaries** - Validates JSON responses before casting
- **Storage failure resilience** - Handles QuotaExceededError gracefully
- **Network error context** - Detailed error messages with method + URL
- **No plaintext tokens in logs** - Tokens never logged or exposed

See [AGENTS.md](../AGENTS.md#lessons-learned-2026-02-06) for detailed security lessons learned.

## Server Integration

This client is designed to work with the Ògiri Spring Boot library. See the [main README](../README.md) for server setup.

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
