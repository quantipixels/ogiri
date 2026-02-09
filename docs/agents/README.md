# Agent Documentation Index

Detailed guidance for AI coding assistants and contributors working in the Ògiri repository.

## Core Documents

- **[Architecture & Repository Structure](architecture.md)** - Module organization, packages, extension points
- **[Testing Conventions](testing.md)** - JUnit patterns, running tests, coverage requirements
- **[Code Style Guidelines](code-style.md)** - Formatting rules, imports, Kotlin/Java conventions
- **[Security Invariants](security.md)** - Authentication flow, token handling, security requirements
- **[Performance Patterns](performance.md)** - Caching strategies, indexing, optimization approaches
- **[Git & Workflow](git-workflow.md)** - Commit messages, branching, PRs, licensing
- **[Configuration Reference](configuration.md)** - All `ogiri.*` properties and their defaults

## Quick Navigation

### By Task Type

**Implementing new features:**

1. Read [Architecture](architecture.md) - understand module boundaries
2. Check [Security](security.md) - ensure auth flow compliance
3. Follow [Testing](testing.md) - write tests first
4. Apply [Code Style](code-style.md) - formatting and conventions

**Fixing bugs:**

1. Check [Testing](testing.md) - write failing test first
2. Review [Security](security.md) - ensure fix doesn't introduce vulnerabilities
3. Verify [Performance](performance.md) - fix doesn't degrade performance

**Adding configuration:**

1. Read [Configuration](configuration.md) - understand existing properties
2. Follow naming conventions (`ogiri.*` prefix)
3. Document defaults and behavior

## Parent Document

See [AGENTS.md](../../AGENTS.md) in the repository root for quick-start guidance and recent lessons learned.
