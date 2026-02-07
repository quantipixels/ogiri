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

## Lessons Learned (2026-02-06)

### Client Library Error Handling

**Context:** Code review of ogiri-client TypeScript package revealed systematic error handling issues.

**Key improvements applied:**
- Type safety at boundaries (no unsafe casting)
- Storage failure resilience (QuotaExceededError handling)
- Browser API limitations (Cookie header, btoa() edge cases)
- Partial state detection (token rotation header diagnostics)
- Network error context (method + URL in all errors)
- TypeScript exhaustiveness checks

**Applied to:** ogiri-client package (client.ts, token-storage.ts, interceptors.ts)

> Detailed patterns documented in global error-handling rules.

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
