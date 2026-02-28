# Ògiri Security Client — Retired

> **This package is no longer published.**
>
> The auth primitives have been inlined into the sample app and documented as copy-paste patterns.
> See [`sample/sample-react/src/lib/auth.ts`](../sample/sample-react/src/lib/auth.ts) and
> [`docs/react-integration.md`](../docs/react-integration.md).

## Why

The client was designed as "distroless" (zero mandatory dependencies, BYO HTTP client). Taken to its
conclusion, a 200-line auth primitive that consumers are encouraged to own doesn't belong in a
versioned npm package — it belongs in a sample they copy and adapt.

The `ogiri-security-client/axios` sub-entrypoint made the same point more sharply: the axios
adapter is 60 lines of mechanical wiring that every project that uses it customises anyway.

## What to use instead

Copy [`src/lib/auth.ts`](../sample/sample-react/src/lib/auth.ts) and optionally
[`src/lib/axios-ogiri.ts`](../sample/sample-react/src/lib/axios-ogiri.ts) into your project.
They are self-contained, fully typed, and have no runtime dependencies.

See [`docs/react-integration.md`](../docs/react-integration.md) for a complete integration guide
covering axios, ky, ofetch, and native fetch.

## Source

The source in `src/` is kept as a reference. It is not published to npm (`"private": true`).

## License

Apache License 2.0
