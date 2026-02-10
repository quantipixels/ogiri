# Ògiri Agent Guide

Kotlin Spring Boot 3.5+ security library for token-based authentication.

## Quick Start

**Java Version:** This project requires **Java 17** for development. Java 25 is not supported due to tooling incompatibilities (Kotlin compiler, Spotless/google-java-format).

**Kotlin/Java (Gradle):**

```bash
java -version                # Should show Java 17
./gradlew build              # Build & test all
./gradlew spotlessApply      # Format code
./gradlew :ogiri-core:test   # Core tests only
```

**TypeScript Client (pnpm):**

```bash
cd ogiri-client
pnpm install                 # Install dependencies (ALWAYS use pnpm, not npm/yarn)
pnpm build                   # Build for production
pnpm test                    # Run tests
pnpm test -- --coverage      # Run tests with coverage
```

> **Note:** Always use `pnpm` for TypeScript/Node.js operations in this repository.

## Critical Rules

⚠️ **NEVER log raw tokens, passwords, or credentials**

## Lessons Learned

### Client Library Error Handling (2026-02-06)

**Context:** Code review of ogiri-client TypeScript package revealed systematic error handling issues.

**Key improvements applied:**

- Type safety at boundaries (no unsafe casting)
- Storage failure resilience (QuotaExceededError handling)
- Browser API limitations (Cookie header, btoa() edge cases)
- Partial state detection (token rotation header diagnostics)
- Network error context (method + URL in all errors)
- TypeScript exhaustiveness checks

**Applied to:** ogiri-client package (auth.ts, fetch-client.ts, token-storage.ts, interceptors.ts)

> Detailed patterns documented in global error-handling rules.

### Repeated Mistakes (2026-02-10)

Patterns that keep recurring. Read before implementing.

**1. Understand domain constraints before reaching for patterns.**
Tokens are keyed by `(userId, client)` — one device = one writer. There is no write contention. Don't add retry/locking/transaction machinery for a problem that doesn't exist. Ask "does the data model actually have this problem?" before engineering a solution.

**2. Verify code after spotless runs.**
The spotless formatter (PostToolUse hook) can silently strip new constants, init blocks, and structural additions it doesn't recognize. After adding multi-line structural code, re-read the file to confirm it survived formatting.

**3. When agents crash with internal errors, use direct tools.**
`classifyHandoffIfNeeded is not defined` and similar internal errors are not recoverable by re-spawning. Fall back to direct Grep/Read/Edit instead of retrying the same agent type.

**4. Trace call paths before assuming code is live.**
`findTokenCandidates()` had zero callers in the production auth flow but existed across 14 files with a DoS vector. Before modifying or building on existing code, verify it has actual callers. `grep` for the method name in non-test files.

**5. Read the implementation before writing mocks or docs.**
MSW mocks returned kebab-case keys; queries expected camelCase. README showed async API; implementation was sync. Always read the source of truth (the implementation) before writing anything that depends on its interface.

**6. User interruptions mean wrong direction.**
High `[Request interrupted]` rate correlates with: excessive verbosity, not researching before implementing, or heading somewhere the user didn't ask for. Be concise. Research first. Ask before building.

## Detailed Documentation

For comprehensive agent guidelines on architecture, testing, security, and more, see **[docs/agents/](docs/agents/)**.

> **Important:** For project-specific patterns, token handling, and architectural decisions, prefer reading the linked documentation files over relying on general framework knowledge.

Quick links:

- [Architecture & Repo Structure](docs/agents/architecture.md) - Modules, packages, extension points
- [Testing Conventions](docs/agents/testing.md) - JUnit patterns, running tests, coverage
- [Code Style Guidelines](docs/agents/code-style.md) - Formatting, imports, Kotlin/Java style
- [Security Invariants](docs/agents/security.md) - Auth flow, token handling, error handling
- [Performance Patterns](docs/agents/performance.md) - Caching, indexing, optimization strategies
- [Git & Workflow](docs/agents/git-workflow.md) - Commits, branching, PRs, licensing
- [Configuration Reference](docs/agents/configuration.md) - All `ogiri.*` properties and defaults
