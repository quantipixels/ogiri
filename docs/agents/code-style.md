# Code Style Guidelines

## Formatting (Spotless + .editorconfig)

### Auto-formatting

```bash
./gradlew spotlessApply     # Format all code
./gradlew spotlessCheck     # Check formatting
```

### Formatter Configuration

| Language               | Formatter          | Version                   |
| ---------------------- | ------------------ | ------------------------- |
| **Kotlin/KTS**         | ktfmt              | 0.43                      |
| **Java**               | google-java-format | 1.22.0                    |
| **Markdown/YAML/TOML** | Prettier           | (trim + trailing newline) |
| **SQL**                | DBeaver profile    | -                         |

⚠️ **Do not modify formatter versions**

### .editorconfig Rules

- **Default**: 4-space indent, 120-char width
- **Kotlin/KTS/YAML/MD**: 2-space indent
- **Line endings**: LF
- **Trailing whitespace**: Trim
- **Final newline**: Required

## License Headers

- **Spotless automatically injects** license headers from `spotless.license.kt`
- **Do not manually add** license headers to new files
- **Preserve existing** license headers when editing files

## Imports

- **Avoid fully qualified names** in code when import is possible

### Import Order

1. Kotlin/Java stdlib
2. Third-party libraries
3. Project packages
4. Static imports (after regular)

## Kotlin Style

### General

- **Indentation**: 2 spaces (no tabs)
- **Names**: `camelCase` for functions/properties, `PascalCase` for classes/interfaces/objects

### Null Safety

- **Prefer** nullable types over `!!`
- **Only use `!!`** in test code

### Immutability

- Use `data classes` where appropriate
- Favor `val` over `var`

### Functions

- Use **expression bodies** for simple returns
- Favor **`when`** over cascaded `if` where clear
- Extract helpers in same file when scoped

### Type Safety

- Prefer **`sealed`/`enum`** for constrained types
- Avoid magic strings

## Java Style

- **Follow Google Java Format** (2-space indent via formatter)
- **Avoid Lombok** - use standard constructors/builders
- **Prefer `final` fields** where possible
- Avoid mutable statics
- Use `Optional` sparingly; favor Kotlin nullability patterns in mixed code

## Tooling

- **Java 17+** required
- **Kotlin 2.1.0** (toolchain configured)
- **Gradle 8.x** wrapper present
- ⚠️ **Do not bump tool versions** without approval
