# Contributing to ogiri

Thank you for your interest in contributing to **ogiri**! This document provides guidelines and instructions for getting involved with the project.

## Code of Conduct

We are committed to providing a welcoming and inclusive environment for all contributors. Please read and follow our [Code of Conduct](./CODE_OF_CONDUCT.md) (coming soon).

## Ways to Contribute

### 1. Reporting Bugs

Found a bug? Help us fix it!

**Before reporting:**
- Check if the issue already exists on [GitHub Issues](https://github.com/mosobande/ogiri/issues)
- Review the [README.md](./README.md) and documentation to rule out configuration issues
- Check [SECURITY.md](./SECURITY.md) if it's a security concern

**When reporting:**
```markdown
**Description:** Clear description of the bug

**Affected Version:** v1.0.0 (or commit hash)

**Steps to Reproduce:**
1. Step one
2. Step two
3. Expected behavior vs. actual behavior

**Environment:**
- Java version: 17
- Spring Boot version: 3.5.7
- Database: PostgreSQL/MySQL/MongoDB
- OS: macOS/Linux/Windows
```

### 2. Proposing Features

Have an idea for improvement? We'd love to hear it!

**Before proposing:**
- Check if similar feature requests already exist
- Consider if the feature aligns with ogiri's mission (token-based auth, database-agnostic)
- Assess impact on existing functionality

**When proposing:**
```markdown
**Title:** Feature summary

**Problem:** What problem does this solve?

**Solution:** How should this feature work?

**Alternatives:** Other approaches considered?

**Additional Context:** Related issues, examples, references
```

### 3. Code Contributions

### Getting Started

1. **Fork the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/ogiri.git
   cd ogiri
   ```

2. **Create a branch**
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```

3. **Read the development guide**
   See [CLAUDE.md](./CLAUDE.md) for architecture, project structure, and development workflow.

### Development Setup

```bash
# Prerequisites
- Java 17+
- Kotlin 2.0.x
- Gradle 8.x

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a sample app
./gradlew :sample:sample-java:bootRun
# or
./gradlew :sample:sample-kotlin:bootRun

# Check code formatting
./gradlew spotlessCheck

# Auto-format code
./gradlew spotlessApply
```

### Code Guidelines

#### Java/Kotlin Style

- Follow existing code style in the project
- Format code with `./gradlew spotlessApply` before committing
- Use meaningful variable and function names
- Add JavaDoc for public APIs
- Keep lines under 120 characters where reasonable

#### Testing

- Write tests for new features
- Ensure all tests pass: `./gradlew test`
- Aim for >80% code coverage
- Test both success and failure paths
- Use descriptive test names

**Test example:**
```kotlin
@Test
fun `should create token with correct expiration`() {
    // Given
    val userId = 1L
    val expiry = Instant.now().plus(1, ChronoUnit.HOURS)

    // When
    val token = tokenService.createNewAuthToken(userId, mockRequest, mockResponse)

    // Then
    assertThat(token.expiryAt).isEqualTo(expiry)
}
```

#### Database Changes

If your contribution modifies the token schema:
- Update all bundled schema files (PostgreSQL, MySQL, H2, MongoDB)
- Update the schema requirements table in README.md
- Add migration examples to docs/DATABASE.md
- Ensure backward compatibility if possible

#### Documentation

- Update relevant documentation files
- Include JavaDoc for public APIs
- Add examples for new features
- Update CHANGELOG.md with your changes

### Commit Guidelines

```bash
# Good commit messages
git commit -m "feat: add token revocation endpoint"
git commit -m "fix: prevent expired token renewal"
git commit -m "docs: add multi-tenant setup guide"
git commit -m "test: add edge case for concurrent token creation"
git commit -m "refactor: extract common validation logic"

# Use conventional commits format:
# feat: new feature
# fix: bug fix
# docs: documentation changes
# test: test additions/updates
# refactor: code reorganization
# perf: performance improvements
# chore: maintenance tasks
```

### Submitting a Pull Request

1. **Ensure your code passes all checks**
   ```bash
   ./gradlew build spotlessCheck test
   ```

2. **Push to your fork**
   ```bash
   git push origin feature/your-feature-name
   ```

3. **Create a Pull Request**
   - Link related issues with `Fixes #123` or `Related to #456`
   - Describe your changes clearly
   - Explain why this change is needed
   - Note any breaking changes

   **PR template:**
   ```markdown
   ## Description
   [Brief description of changes]

   ## Motivation
   Fixes #[issue_number]
   [Why is this change needed?]

   ## Changes
   - [Change 1]
   - [Change 2]

   ## Type of Change
   - [ ] Bug fix
   - [ ] New feature
   - [ ] Documentation update
   - [ ] Breaking change

   ## Testing
   - [ ] Unit tests added/updated
   - [ ] Integration tests added/updated
   - [ ] Manual testing performed

   ## Checklist
   - [ ] Code follows project style guidelines
   - [ ] Tests pass locally
   - [ ] Documentation updated
   - [ ] No new warnings generated
   - [ ] CHANGELOG.md updated
   ```

4. **Respond to review feedback**
   - All reviews are constructive, not critical
   - Respond to each comment
   - Request re-review when ready

5. **Merge eligibility**
   - All checks pass
   - At least one approval
   - No merge conflicts

## Project Structure

```
ogiri/
├── ogiri-core/              # Main library (published to Maven Central)
│   ├── src/main/kotlin/     # Source code
│   ├── src/test/kotlin/     # Tests
│   ├── src/main/resources/  # Resources (including bundled schemas)
│   └── build.gradle.kts     # Library build configuration
├── sample/
│   ├── sample-java/         # Pure Java example
│   ├── sample-kotlin/       # Kotlin example
│   └── README.md            # Sample setup guide
├── docs/                    # Documentation
│   ├── AUTHENTICATION.md    # Token flow and rotation
│   └── DATABASE.md  # Database setup patterns
├── .github/workflows/       # GitHub Actions CI/CD
├── CLAUDE.md                # Development guidelines
├── SECURITY.md              # Security policy
├── CHANGELOG.md             # Release notes
└── README.md                # Main documentation
```

## Areas for Contribution

### High Priority

- [ ] R2DBC examples for reactive SQL
- [ ] Spring Data JDBC integration guide
- [ ] GraphQL authentication example
- [ ] Multi-tenant token management patterns
- [ ] Performance benchmarking and optimization

### Medium Priority

- [ ] Additional NoSQL examples (Firestore, DynamoDB)
- [ ] Advanced authentication scenarios (OAuth2 integration)
- [ ] Rate limiting examples
- [ ] Token analytics and monitoring guide

### Low Priority

- [ ] Additional language examples (Scala, Groovy)
- [ ] Build time optimization
- [ ] Documentation improvements
- [ ] Sample refactoring

## Useful Resources

- [Spring Security Docs](https://spring.io/projects/spring-security)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Project README](./README.md) - Overview and quick start
- [Development Guide](./CLAUDE.md) - Architecture and workflows

## Getting Help

- **Questions?** Open a GitHub Discussion or issue
- **Need help?** Check documentation or comment on related PR
- **Security concern?** See [SECURITY.md](./SECURITY.md)
- **Feature discussion?** Start an issue with feature proposal

## Recognition

All contributors are recognized in:
- GitHub contributor list
- CHANGELOG.md release notes
- Project acknowledgments

Thank you for contributing to ogiri! 🙏

---

**Last Updated:** December 5, 2025
**Maintainer:** mosobande
