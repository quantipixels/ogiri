/**
 * Apply centralized project version to all subprojects.
 *
 * Version is defined in .ogiri-version file as the single source of truth.
 * This script applies it to all modules (ogiri-core, sample-java, sample-kotlin).
 *
 * Version sources (in order of precedence):
 * 1. Environment variable RELEASE_VERSION - Manual releases & CI/CD
 * 2. Gradle property -PRELEASE_VERSION - Local builds
 * 3. .ogiri-version file - Default version
 * 4. Fallback: UNVERSIONED (if file doesn't exist)
 *
 * Usage:
 *   gradle build                              # Uses version from .ogiri-version
 *   RELEASE_VERSION=1.0.3 gradle build       # Uses 1.0.3
 *   gradle -PRELEASE_VERSION=1.0.3 build     # Uses 1.0.3
 */

// Read version from .ogiri-version file
val versionFile = rootProject.file(".ogiri-version")
val versionFromFile = if (versionFile.exists()) {
  versionFile.readText().trim().takeIf { it.isNotBlank() }
} else {
  null
}

val envVersion = System.getenv("RELEASE_VERSION")
val propVersion = System.getProperty("RELEASE_VERSION")

// Version resolution with proper precedence
val resolvedVersion = envVersion ?: propVersion ?: versionFromFile ?: "UNVERSIONED"

// Apply to all subprojects
rootProject.allprojects {
  version = resolvedVersion
}
