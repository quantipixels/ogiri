import type { OgiriRequestOptions, OgiriResponse } from "./types";
import type { OgiriAuth } from "./auth";

/**
 * Thin convenience wrapper over native fetch.
 * Delegates all auth state management to OgiriAuth.
 * Optional — consumers who use axios/ky/ofetch can ignore this entirely.
 */
export class OgiriFetchClient {
    private readonly baseURL: string;

    constructor(
        private readonly auth: OgiriAuth,
        baseURL: string,
    ) {
        this.baseURL = baseURL.replace(/\/$/, "");
    }

    async request<T = unknown>(
        path: string,
        options: OgiriRequestOptions & { method?: string } = {},
    ): Promise<OgiriResponse<T>> {
        const { params, body, method = "GET", ...fetchOptions } = options;

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
                "Content-Type": "application/json",
            };
        }

        config = this.auth.injectInto(config);

        let response: Response;
        try {
            response = await fetch(url, config);
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            throw new Error(`Network request failed for ${method} ${url}: ${message}`);
        }

        if (response.status === 401) {
            const errorBody = await this.safeParseJson(response);
            throw this.auth.handleAuthError(errorBody);
        }

        if (!response.ok) {
            const errorBody = await this.safeParseJson(response);
            const errorMessage =
                typeof errorBody === "object" && errorBody !== null && "message" in errorBody
                    ? String((errorBody as { message: unknown }).message)
                    : response.statusText;
            const error = new Error(`HTTP ${response.status}: ${errorMessage}`);
            (error as any).status = response.status;
            (error as any).body = errorBody;
            throw error;
        }

        this.auth.extractFrom(response);

        const data = await this.safeParseJson(response);

        if (typeof data === "string") {
            throw new Error(`Expected JSON from ${url}, received unparseable text: ${data.substring(0, 100)}`);
        }

        return { data: data as T, response };
    }

    async get<T = unknown>(path: string, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
        return this.request<T>(path, { ...options, method: "GET" });
    }

    async post<T = unknown>(path: string, body: unknown, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
        return this.request<T>(path, { ...options, method: "POST", body });
    }

    async put<T = unknown>(path: string, body: unknown, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
        return this.request<T>(path, { ...options, method: "PUT", body });
    }

    async delete<T = unknown>(path: string, options?: OgiriRequestOptions): Promise<OgiriResponse<T>> {
        return this.request<T>(path, { ...options, method: "DELETE" });
    }

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
