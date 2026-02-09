# Git & Workflow

## Commit Style

Use **Conventional Commits**:

- `feat:` - New feature
- `fix:` - Bug fix
- `refactor:` - Code restructuring
- `docs:` - Documentation changes
- `test:` - Test additions/changes
- `chore:` - Maintenance tasks

## Pre-commit

Run before pushing (when feasible):

```bash
./gradlew build spotlessCheck
```

## Git Rules

- **Do not change git config**

## Branching

- **Base branch**: `ori`
- Keep PRs **focused** on single concern
- Reference related issues in PR description

## Pull Requests

- **Include tests** with behavior changes
- **Note coverage impacts** in description
- Keep changes cohesive and reviewable

## Licensing

- Spotless **automatically injects** license headers from `spotless.license.kt`
- **Do not manually add** license headers to new files
- **Preserve existing** license headers when editing Kotlin/Java files
