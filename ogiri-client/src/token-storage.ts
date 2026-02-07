import type { OgiriTokens, TokenStorage } from './types';

/**
 * In-memory token storage (SSR-safe, default)
 */
export class MemoryTokenStorage implements TokenStorage {
  private tokens: OgiriTokens | null = null;

  get(): OgiriTokens | null {
    return this.tokens;
  }

  set(tokens: OgiriTokens): void {
    this.tokens = tokens;
  }

  clear(): void {
    this.tokens = null;
  }
}

/**
 * Browser localStorage-based token storage
 */
export class LocalStorageTokenStorage implements TokenStorage {
  constructor(private readonly key: string = 'ogiri-tokens') {}

  get(): OgiriTokens | null {
    try {
      const stored = localStorage.getItem(this.key);
      return stored ? JSON.parse(stored) : null;
    } catch (err) {
      // C3: Distinguish between JSON parse errors and storage access errors
      if (err instanceof SyntaxError) {
        console.warn('Invalid JSON in token storage, clearing...');
        try {
          localStorage.removeItem(this.key);
        } catch {
          // Ignore errors when clearing
        }
        return null;
      }
      // Storage access error (blocked, quota exceeded, etc.)
      console.error('Failed to access localStorage:', err instanceof Error ? err.message : String(err));
      return null;
    }
  }

  set(tokens: OgiriTokens): void {
    // C2: Handle localStorage errors (QuotaExceededError, SecurityError)
    try {
      localStorage.setItem(this.key, JSON.stringify(tokens));
    } catch (err) {
      console.error('Failed to save tokens to localStorage:', err instanceof Error ? err.message : String(err));
      // Don't throw - storage failure should not kill the API response
    }
  }

  clear(): void {
    // C2: Handle localStorage errors during clear
    try {
      localStorage.removeItem(this.key);
    } catch (err) {
      console.error('Failed to clear tokens from localStorage:', err instanceof Error ? err.message : String(err));
      // Don't throw - this is called from 401 path, must not block error handling
    }
  }
}
