/**
 * Ogiri authentication tokens (camelCase properties match server headers)
 */
export interface OgiriTokens {
  accessToken: string;
  client: string;
  uid: string;
  expiry: string;
  tokenType: string;
  /** H4: Token kind (APP, SUB, etc.) - sent as access-token-kind header */
  tokenKind?: string;
}

/**
 * Authentication method for Ogiri requests
 */
export type OgiriAuthMethod = 'headers' | 'bearer' | 'cookies';

/**
 * Token storage interface
 */
export interface TokenStorage {
  get(): OgiriTokens | null;
  set(tokens: OgiriTokens): void;
  clear(): void;
}

/**
 * Client configuration
 */
export interface OgiriClientConfig {
  baseURL: string;
  authMethod?: OgiriAuthMethod;
  storage?: TokenStorage;
  /** M3: Callback receives OgiriAuthError, not generic Error */
  onAuthError?: (error: OgiriAuthError) => void;
}

// Forward declaration for OgiriAuthError type
export interface OgiriAuthError extends Error {
  status: number;
  body: unknown;
}

/**
 * Request options
 */
export interface OgiriRequestOptions extends Omit<RequestInit, 'method' | 'body'> {
  params?: Record<string, string>;
  body?: unknown;
}

/**
 * Response wrapper
 */
export interface OgiriResponse<T = unknown> {
  data: T;
  response: Response;
}
