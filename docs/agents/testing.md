# Testing Guidelines

## Framework

- **JUnit 5** with Spring test utilities
- **Location**: `src/test/kotlin/<package>/<Name>Test.kt`
- **Coverage**: Generated at `ogiri-core/build/reports/jacoco/test/html/index.html`

## Test Naming

Use backticked descriptive names:

```kotlin
@Test
fun `should rotate token outside batch window`() { ... }
```

## Running Tests

### All Tests
```bash
./gradlew test                    # All modules
./gradlew :ogiri-core:test        # Core only
```

### Single Test Class
```bash
# By fully qualified name (preferred)
./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.core.AuthHeaderTest"

# By pattern
./gradlew :ogiri-core:test --tests "*AuthHeaderTest"
```

### Single Test Method
```bash
# With backticked name
./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.tokens.TokenServiceSubTokenTest.`default sub token is issued and returned in headers`"

# Nested test class (use $ separator)
./gradlew :ogiri-core:test --tests "com.quantipixels.ogiri.security.core.AuthHeaderTest\$ParseBearerTokenTests.`parseBearerToken parses valid token`"
```

### Re-run Failed Tests
```bash
./gradlew :ogiri-core:test --tests $(cat ogiri-core/build/test-results/test/TESTS-failed.txt)
```
(if `TESTS-failed.txt` exists)

## Test Strategy

- **Prefer in-memory fakes** (`InMemoryTokenRepository`) over real databases
- **Keep tests deterministic**: Use fixed `Instant` values to avoid clock drift
- When modifying token logic, add/adjust `AuthHeader` serialization tests
- If changing schemas/resources, sync persistence tests and bundled SQL under `ogiri/db`

## Test Organization

- Use nested test classes for grouping related tests
- Example: `AuthHeaderTest$ParseBearerTokenTests`
