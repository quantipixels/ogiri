import type {
  OgiriClientConfig,
  OgiriRequestOptions,
  OgiriResponse,
  OgiriTokens,
  OgiriAuthMethod,
  TokenStorage,
} from './types';
import { MemoryTokenStorage } from './token-storage';
import { injectAuth, extractTokens } from './interceptors';
import { OgiriAuthError } from './errors';

/**
 * Ogiri HTTP client with automatic token rotation
 */
export class OgiriClient {
  private readonly baseURL: string;
  private readonly authMethod: OgiriAuthMethod;
  private readonly storage: TokenStorage;
  private readonly onAuthError?: (error: OgiriAuthError) => void;

  constructor(config: OgiriClientConfig) {
    this.baseURL = config.baseURL.replace(/\/$/, '');
    this.authMethod = config.authMethod ?? 'headers';
    this.storage = config.storage ?? new MemoryTokenStorage();
    this.onAuthError = config.onAuthError;
  }

  /**
   * Make HTTP request with automatic auth injection and token rotation
   */
  async request<T = unknown>(
    path: string,
    options: OgiriRequestOptions & { method?: string } = {}
  ): Promise<OgiriResponse<T>> {
    const { params, body, method = 'GET', ...fetchOptions } = options;

    let url = `${this.baseURL}${path}`;
    if (params) {
      const query = new URLSearchParams(params).toString();
      url += `?${query}`;
    }

    let config: RequestInit = {
      ...fetchOptions,
      method,
    };

    if (body) {
      config.body = JSON.stringify(body);
      config.headers = {
        ...config.headers,
        'Content-Type': 'application/json',
      };
    }

    const tokens = this.storage.get();
    if (tokens) {
      config = injectAuth(config, tokens, this.authMethod);
    }

    // H1: Wrap fetch to provide better network error context
    let response: Response;
    try {
      response = await fetch(url, config);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      throw new Error(`Network request failed for ${method} ${url}: ${message}`);
    }

    if (response.status === 401) {
      this.storage.clear();
      const errorBody = await this.safeParseJson(response);
      const error = new OgiriAuthError('Unauthorized', 401, errorBody);
      this.onAuthError?.(error);
      throw error;
    }

    // H2: Parse error body for non-401 errors
    if (!response.ok) {
      const errorBody = await this.safeParseJson(response);
      const errorMessage = typeof errorBody === 'object' && errorBody !== null && 'message' in errorBody
        ? String((errorBody as { message: unknown }).message)
        : response.statusText;
      const error = new Error(`HTTP ${response.status}: ${errorMessage}`);
      (error as any).status = response.status;
      (error as any).body = errorBody;
      throw error;
    }

    const rotatedTokens = extractTokens(response);
    if (rotatedTokens) {
      this.storage.set(rotatedTokens);
    }

    const data = await this.safeParseJson(response);

    // C1: Prevent raw HTML/text from being cast as T
    if (typeof data === 'string') {
      throw new Error(`Expected JSON from ${url}, received unparseable text: ${data.substring(0, 100)}`);
    }

    return { data: data as T, response };
  }

  /**
   * GET request
   */
  async get<T = unknown>(path: string, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
    return this.request<T>(path, { ...options, method: 'GET' });
  }

  /**
   * POST request
   */
  async post<T = unknown>(
    path: string,
    body: unknown,
    options?: OgiriRequestOptions
  ): Promise<OgiriResponse<T>> {
    return this.request<T>(path, { ...options, method: 'POST', body });
  }

  /**
   * PUT request
   */
  async put<T = unknown>(
    path: string,
    body: unknown,
    options?: OgiriRequestOptions
  ): Promise<OgiriResponse<T>> {
    return this.request<T>(path, { ...options, method: 'PUT', body });
  }

  /**
   * DELETE request
   */
  async delete<T = unknown>(path: string, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
    return this.request<T>(path, { ...options, method: 'DELETE' });
  }

  /**
   * Get current tokens
   */
  getTokens(): OgiriTokens | null {
    return this.storage.get();
  }

  /**
   * Set tokens manually (e.g., after login)
   */
  setTokens(tokens: OgiriTokens): void {
    this.storage.set(tokens);
  }

  /**
   * Clear tokens (logout)
   */
  clearTokens(): void {
    this.storage.clear();
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return this.storage.get() !== null;
  }

  /**
   * Safely parse JSON response
   */
  private async safeParseJson(response: Response): Promise<unknown> {
    const text = await response.text();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch (e) {
      console.error(`Failed to parse JSON response: ${e instanceof Error ? e.message : String(e)}`);
      return text;
    }
  }
}
