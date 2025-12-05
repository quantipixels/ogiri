/**
 * Apply centralized project version to all subprojects.
 *
 * Version is defined in settings.gradle.kts as the single source of truth.
 * This script applies it to all modules (ogiri-core, sample-java, sample-kotlin).
 *
 * Version sources (in order of precedence):
 * 1. Git tag (v1.0.1, v1.0.2, etc.) - CI/CD releases
 * 2. Environment variable RELEASE_VERSION - Manual releases
 * 3. Gradle property -PRELEASE_VERSION - Local builds
 * 4. Default: 1.0.1 (defined in settings.gradle.kts)
 *
 * Usage:
 *   gradle build                              # Uses 1.0.1
 *   RELEASE_VERSION=1.0.2 gradle build       # Uses 1.0.2
 *   gradle -PRELEASE_VERSION=1.0.2 build     # Uses 1.0.2
 *   git tag v1.0.2 && git push origin v1.0.2 # CI/CD: Uses 1.0.2
 */

// Get version from settings.gradle.kts (projectVersion variable)
// Falls back to detecting from git tags if not set
val gitTag = try {
  Runtime.getRuntime().exec("git describe --tags --abbrev=0 --always").inputStream.bufferedReader().readText().trim()
} catch (e: Exception) {
  null
}

val versionFromGit = gitTag?.let {
  if (it.matches(Regex("v?\\d+\\.\\d+\\.\\d+.*"))) {
    it.removePrefix("v")
  } else {
    null
  }
}

val envVersion = System.getenv("RELEASE_VERSION")
val propVersion = System.getProperty("RELEASE_VERSION")
val ciVersion = System.getenv("CI_COMMIT_TAG")?.removePrefix("v")

// Version resolution with proper precedence
val resolvedVersion = versionFromGit ?: envVersion ?: propVersion ?: ciVersion ?: "1.0.1"

// Apply to all subprojects
rootProject.allprojects {
  version = resolvedVersion
}
