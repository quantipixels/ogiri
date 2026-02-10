/**
 * Authentication error thrown when requests fail with 401
 */
export class OgiriAuthError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly body: unknown,
    ) {
        super(message);
        this.name = "OgiriAuthError";
    }
}
