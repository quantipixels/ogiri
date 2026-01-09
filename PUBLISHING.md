# Publishing Guide

This document explains how to build and use the Ogiri Security library.

## Modules

- **ogiri-core**: Core security library (database-agnostic)
- **ogiri-jpa**: JPA adapter module (optional, for Spring Data JPA integration)

## Publishing Methods

### 1. Local Maven (Development)

Install both modules to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Use in your project:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.quantipixels.ogiri:ogiri-core:1.3.0")
    implementation("com.quantipixels.ogiri:ogiri-jpa:1.3.0")  // Optional
}
```

### 2. JitPack (Public Access)

JitPack builds directly from GitHub tags/branches.

#### Usage

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.quantipixels.ogiri:ogiri-core:TAG")
    implementation("com.github.quantipixels.ogiri:ogiri-jpa:TAG")  // Optional
}
```

Replace `TAG` with:

- A release tag: `1.3.0`
- A commit hash: `abc123`
- A branch name: `main-SNAPSHOT`

#### Triggering Builds

Visit https://jitpack.io/#quantipixels/ogiri to trigger builds for specific tags.

### 3. Maven Central (Production)

Publishing to Maven Central requires:

- OSSRH account credentials (`OSSRH_USERNAME`, `OSSRH_PASSWORD`)
- GPG signing keys (`GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`)

```bash
# Publish to OSSRH staging
./gradlew publish

# Or publish locally first, then to remote
./gradlew publishToMavenLocal publish
```

Consumer usage:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.quantipixels.ogiri:ogiri-core:1.3.0")
    implementation("com.quantipixels.ogiri:ogiri-jpa:1.3.0")  // Optional
}
```

## Version Management

The project version is defined in `.ogiri-version` file. To change the version:

1. Edit `.ogiri-version`
2. Commit the change
3. Tag the commit: `git tag v1.3.0`
4. Push: `git push origin v1.3.0`

### Version Override

You can override the version at build time:

```bash
# Using environment variable
RELEASE_VERSION=1.4.0 ./gradlew publishToMavenLocal

# Using Gradle property
./gradlew -PRELEASE_VERSION=1.4.0 publishToMavenLocal
```

## Verification

### Check Local Installation

```bash
ls -la ~/.m2/repository/com/quantipixels/ogiri/ogiri-core/1.3.0/
ls -la ~/.m2/repository/com/quantipixels/ogiri/ogiri-jpa/1.3.0/
```

### Verify POM Content

```bash
cat ~/.m2/repository/com/quantipixels/ogiri/ogiri-core/1.3.0/ogiri-core-1.3.0.pom
cat ~/.m2/repository/com/quantipixels/ogiri/ogiri-jpa/1.3.0/ogiri-jpa-1.3.0.pom
```

Expected artifacts:

- `ogiri-core-1.3.0.jar` - Main library
- `ogiri-core-1.3.0-sources.jar` - Source code
- `ogiri-core-1.3.0-javadoc.jar` - Javadoc
- `ogiri-core-1.3.0.pom` - Maven POM
- `ogiri-core-1.3.0.module` - Gradle metadata

## Troubleshooting

### Gradle Version Compatibility

This project requires Gradle 9.x. If you encounter dependency resolution issues:

1. Check Gradle version: `./gradlew --version`
2. Update Gradle wrapper if needed
3. Clear Gradle cache: `rm -rf ~/.gradle/caches/`

### JitPack Build Failures

If JitPack builds fail:

1. Check build log at https://jitpack.io/com/github/quantipixels/ogiri/TAG/build.log
2. Ensure the tag exists on GitHub
3. Verify `jitpack.yml` is in the repository root

### POM Dependency Issues

If Maven projects can't resolve dependencies:

1. Ensure BOM versions are resolved: Check `versionMapping` in `build.gradle.kts`
2. Verify POM includes `dependencyManagement` section
3. Check that Spring Boot BOM version (3.5.7) is compatible with your project

## Build Configuration Details

### ogiri-core

- Group: `com.quantipixels.ogiri`
- Artifact: `ogiri-core`
- Java: 17
- Kotlin: 2.1.0
- Spring Boot BOM: 3.5.7

### ogiri-jpa

- Group: `com.quantipixels.ogiri`
- Artifact: `ogiri-jpa`
- Depends on: `ogiri-core` (same version)
- Java: 17
- Kotlin: 2.1.0
- Requires: Spring Data JPA

## Related Documentation

- [Main README](README.md) - Project overview and usage
- [SECURITY.md](SECURITY.md) - Security policy
- [CLAUDE.md](CLAUDE.md) - Development guide for Claude Code
