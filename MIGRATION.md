# Project Structure Migration Guide

This document outlines the changes made to support full Java and Spring Boot development, add sample applications, and implement automated CI/CD with Maven Central releases.

## What Changed

### 1. Multi-Module Gradle Build

**Before:** Single module with core library at root level

**After:** Multi-module structure
```
ogiri-security/
├── ogiri-core/          # Moved from root (main library)
├── sample/sample-java/  # NEW: Java integration sample
└── sample/sample-kotlin/# NEW: Kotlin integration sample
```

**Settings Updated:**
- `settings.gradle.kts` now includes module definitions
- Root `build.gradle.kts` provides common configuration
- Each module has its own `build.gradle.kts`

### 2. Java Support

The project now supports Java-only applications without requiring Kotlin on the classpath.

**Sample Java App:** `sample/sample-java/`
- Pure Java source code (no Kotlin dependencies)
- Same SPI implementations as Kotlin version
- TokenUserDirectory, RouteRegistry, TokenRepository examples
- Can be used as a template for Java Spring Boot projects

**Build Configuration:**
- Java sample uses `java` plugin (no Kotlin plugins)
- Kotlin sample uses `kotlin("jvm")` plugin
- Both share Spring Boot and dependency management configuration

### 3. Sample Applications

**Purpose:** Demonstrate how to integrate ogiri in real Spring Boot applications

**Java Sample** (`sample/sample-java/`)
- TokenUserDirectory: In-memory user directory
- RouteRegistry: Declares public routes
- TokenRepository: JPA interface for token persistence
- Controllers: Health check and authenticated endpoints
- Configuration: application.yml with ogiri properties

**Kotlin Sample** (`sample/sample-kotlin/`)
- Same components as Java sample, written in Kotlin
- Shows idiomatic Kotlin patterns (data classes, extension functions)
- Configuration: application.yml with ogiri properties

**Running Samples:**
```bash
# Requires PostgreSQL on localhost:5432
./gradlew :sample:sample-java:bootRun
./gradlew :sample:sample-kotlin:bootRun
```

### 4. Automated CI/CD Pipeline

**New File:** `.gitlab-ci.yml`

Five automated workflows:

1. **build** – Compiles all modules (no tests)
2. **test** – Runs full test suite with coverage
3. **lint** – Validates code formatting (spotlessCheck)
4. **release_maven** – Tag-triggered release to Maven Central
5. **release_manual** – Manual release with custom versioning
6. **deploy_snapshot** – Automatic snapshot deployment on main

### 5. Release Automation

**New File:** `release.gradle.kts`

Gradle tasks for version management:
- `./gradlew bumpVersion` – Increment patch version
- `./gradlew bumpVersion -PnewVersion=X.Y.Z` – Set specific version
- `./gradlew release` – Publish to Maven Central

**New File:** `gradle/version.gradle.kts`

Centralized version management supporting:
- Git tags (e.g., `v1.0.0`)
- Environment variables (`RELEASE_VERSION`, `CI_COMMIT_TAG`)
- Default fallback to build file versions

### 6. Documentation Updates

**Updated:** `README.md`
- Added project structure overview
- Added section on sample applications
- Added release and publishing procedures
- Updated build and test commands

**Created:** `sample/README.md`
- Sample app setup and configuration
- Database setup instructions
- Testing and integration guide
- Troubleshooting tips

**Updated:** `CLAUDE.md`
- Added project structure explanation
- Added CI/CD pipeline documentation
- Added release workflow guide
- Updated development commands
- Added configuration for Maven Central publishing

## Migration for Existing Users

If you're using ogiri in your project, no changes are required. The core library functionality remains the same.

### For New Users

To integrate ogiri in your Spring Boot application:

1. **Add Dependency:**
   ```kotlin
   implementation("com.quantipixels.ogiri:ogiri:0.1.0")
   ```

2. **Implement Required Interfaces:**
   - `TokenUserDirectory` – Load users and record logins
   - `RouteRegistry` – Declare public routes
   - `TokenRepository<Token>` – Persist tokens to database

3. **Refer to Samples:**
   - Java implementation: `sample/sample-java/`
   - Kotlin implementation: `sample/sample-kotlin/`

4. **Configure Application:**
   ```yaml
   ogiri:
     security:
       register-filter: true
     auth:
       batch-grace-seconds: 30
       rotate-on-write-only: false
       rotate-stale-seconds: 3600
   ```

## Development Workflow

### Build and Test
```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Format code
./gradlew spotlessApply

# Run a sample
./gradlew :sample:sample-java:bootRun
```

### Release to Maven Central

#### Automated (GitLab CI/CD)
```bash
# Tag-based release
git tag v1.0.0
git push origin v1.0.0

# Manual release via CI/CD UI
# Set RELEASE_VERSION and trigger release_manual job
```

#### Local Release
```bash
export OSSRH_USERNAME=...
export OSSRH_PASSWORD=...
export GPG_KEY_ID=...
export GPG_PASSPHRASE=...

./gradlew bumpVersion -PnewVersion=1.0.0
./gradlew publish
```

## File Structure Reference

### New Files
- `.gitlab-ci.yml` – CI/CD pipeline configuration
- `release.gradle.kts` – Release automation tasks
- `gradle/version.gradle.kts` – Version management
- `sample/README.md` – Sample app documentation
- `MIGRATION.md` – This file

### Moved Files
- `src/` → `ogiri-core/src/` (core library source)
- `build.gradle.kts` → `ogiri-core/build.gradle.kts` (core build config)

### New Directories
- `sample/sample-java/` – Pure Java sample app
- `sample/sample-kotlin/` – Kotlin sample app

### Updated Files
- `README.md` – Added project structure and release sections
- `settings.gradle.kts` – Added module includes
- `CLAUDE.md` – Added project structure and CI/CD documentation

## Environment Variables for CI/CD

Set these in your GitLab project settings for automated releases:

- `OSSRH_USERNAME` – Sonatype OSSRH username
- `OSSRH_PASSWORD` – Sonatype OSSRH password
- `GPG_KEY_ID` – GPG key ID (short form)
- `GPG_PASSPHRASE` – GPG key passphrase
- `GPG_PRIVATE_KEY` – Base64-encoded GPG private key

To generate the GPG key variable:
```bash
gpg --export-secret-key <KEY_ID> | base64 | tr -d '\n'
```

## Quick Start for Contributors

1. **Clone and build:**
   ```bash
   git clone <repo>
   cd ogiri-security
   ./gradlew build
   ```

2. **Run tests:**
   ```bash
   ./gradlew test
   ./gradlew spotlessCheck
   ```

3. **Make changes:**
   - Edit code in `ogiri-core/src/`
   - Update tests as needed
   - Format with `./gradlew spotlessApply`

4. **Test with samples:**
   ```bash
   ./gradlew :sample:sample-java:bootRun
   ./gradlew :sample:sample-kotlin:bootRun
   ```

5. **Create PR:**
   - Use Conventional Commits
   - Ensure `spotlessCheck` passes
   - Tests must pass

## Troubleshooting

### Build Issues
- Run `./gradlew clean build` to ensure fresh build
- Check Java version: `java -version` (must be 17+)

### Samples Won't Run
- Ensure PostgreSQL is running on localhost:5432
- Create database: `createdb ogiri_sample`
- Import schema: See `sample/README.md`

### Release Issues
- Verify OSSRH credentials in GitLab CI/CD settings
- Ensure GPG key is properly encoded for CI/CD
- Check git tags follow semver: `v*.*.*`

## Support

For questions or issues:
1. Check `CLAUDE.md` for development guidelines
2. Review `sample/README.md` for integration examples
3. See `docs/AUTHENTICATION.md` for token flow details
