# Contributing to Ògiri

Thank you for your interest in contributing!

## Quick Start

```bash
# Clone and build
git clone https://github.com/mosobande/ogiri.git
cd ogiri
./gradlew build

# Run tests
./gradlew test

# Format code
./gradlew spotlessApply
```

## Ways to Contribute

### Reporting Bugs

Before reporting, check [existing issues](https://github.com/mosobande/ogiri/issues).

Include:

- Ògiri version
- Java/Spring Boot version
- Steps to reproduce
- Expected vs actual behavior

### Proposing Features

Open an issue describing:

- Problem being solved
- Proposed solution
- Alternative approaches considered

### Code Contributions

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make changes with tests
4. Format code: `./gradlew spotlessApply`
5. Run tests: `./gradlew test`
6. Push and create PR

## Code Guidelines

- Follow existing code style
- Format with `./gradlew spotlessApply`
- Write tests for new features
- Use descriptive test names: `` `should rotate token outside batch window` ``
- Keep lines under 120 characters

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat: add chat sub-token renewal
fix: prevent expired token renewal
docs: add multi-tenant setup guide
test: add edge case for concurrent token creation
refactor: extract common validation logic
```

## Pull Request Template

```markdown
## Description

Brief description of changes

## Motivation

Fixes #123 / Related to #456

## Changes

- Change 1
- Change 2

## Testing

- [ ] Unit tests added
- [ ] Manual testing performed

## Checklist

- [ ] Tests pass (`./gradlew test`)
- [ ] Code formatted (`./gradlew spotlessApply`)
- [ ] Documentation updated
```

## Areas for Contribution

**High Priority:**

- R2DBC examples for reactive SQL
- Spring Data JDBC integration guide
- GraphQL authentication example
- Performance benchmarking

**Medium Priority:**

- Additional NoSQL examples (Firestore, DynamoDB)
- OAuth2 integration examples
- Rate limiting examples

## Getting Help

- Questions: Open a GitHub Discussion
- Security issues: See [security.md](security.md)
- Development setup: See [development.md](development.md)

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
