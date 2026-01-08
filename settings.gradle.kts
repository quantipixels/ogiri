rootProject.name = "ogiri"

/*
 * Centralized version management.
 *
 * Version is read from .ogiri-version file (primary source of truth).
 * Version determination order (highest to lowest precedence):
 *   1. Environment variable RELEASE_VERSION (CI/CD overrides)
 *   2. Gradle property -PRELEASE_VERSION (local overrides)
 *   3. .ogiri-version file (main version)
 *   4. Default fallback: 1.0.2
 *
 * To update version:
 *   - Edit .ogiri-version file
 *   - Or override: RELEASE_VERSION=1.0.X gradle build
 *   - Or use: scripts/release.sh (creates tag and pushes to GitHub)
 */
val versionFile = File(settingsDir, ".ogiri-version")
val projectVersion =
    System.getenv("RELEASE_VERSION")?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("RELEASE_VERSION")?.takeIf { it.isNotBlank() })
            ?: (if (versionFile.exists()) versionFile.readText().trim() else null)
            ?: "0.0.0-SNAPSHOT"

include(":ogiri-core")

include(":ogiri-jpa")

include(":sample:sample-java")

include(":sample:sample-kotlin")

/*
 * Plugin repositories:
 * Where Gradle downloads plugins declared in plugins {} blocks of build scripts.
 * These repos apply only to plugin lookup, NOT library dependencies.
 */
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

/*
 * Dependency resolution configuration for ALL modules.
 *
 * Defines:
 *   - Global repositories (Maven Central, Local, custom)
 *   - Version catalogs (libs.*)
 */
dependencyResolutionManagement {

  /*
   * Centralized repository definitions.
   *
   * These repositories apply to:
   *   - dependencies { implementation(...) }
   *   - dependencyManagement
   *   - test dependencies
   *   - version catalog dependencies
   *
   * Best practice: keep ALL repository declarations here.
   */
  repositories { mavenCentral() }

  /*
   * Version catalog:
   *
   * Provides a typed, IDE-aware way to define dependency versions and reuse them across modules.
   * Example usage:
   *   implementation(libs.junit)
   *   version(libs.versions.kotlin.get())
   */
  versionCatalogs {
    create("libs") {
      // Plugin versions
      version("kotlin", "2.1.0")
      version("spotless", "8.0.0")
      version("springBoot", "3.5.7")
      version("dependencyManagement", "1.1.7")
      version("versionsPlugin", "0.52.0")
    }
  }
}
