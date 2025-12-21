/**
 * Release configuration for ogiri project.
 *
 * Usage:
 * - Automated release via GitLab CI/CD: See .gitlab-ci.yml
 * - Manual release: ./gradlew release -Pversion=X.Y.Z
 * - Snapshot deployment: Automatic on main branch
 *
 * Environment variables required for Maven Central publishing:
 * - OSSRH_USERNAME: Sonatype OSSRH username
 * - OSSRH_PASSWORD: Sonatype OSSRH password
 * - GPG_KEY_ID: GPG key ID for signing
 * - GPG_PASSPHRASE: GPG key passphrase
 * - GPG_PRIVATE_KEY: GPG private key (base64 encoded for CI/CD)
 *
 * Version bumping:
 * - Major: breaking changes, e.g., 0.1.0 -> 1.0.0
 * - Minor: new features, e.g., 0.1.0 -> 0.2.0
 * - Patch: bug fixes, e.g., 0.1.0 -> 0.1.1
 */
tasks.register("release") {
  group = "release"
  description = "Release the project to Maven Central"

  finalizedBy("publish")

  doLast {
    val version = project.findProperty("version")?.toString() ?: "0.1.0"
    val isSnapshot = version.endsWith("-SNAPSHOT")

    println("📦 Releasing version: $version")
    println("🔐 Snapshot: $isSnapshot")
    println("✅ Ready to publish to Maven Central")
  }
}

tasks.register("bumpVersion") {
  group = "versioning"
  description = "Bump patch version in all build files"

  doLast {
    val currentVersion =
        project.findProperty("currentVersion")?.toString()
            ?: System.getenv("CURRENT_VERSION") ?: "0.1.0-SNAPSHOT"
    val newVersion =
        project.findProperty("newVersion")?.toString()
            ?: System.getenv("NEW_VERSION") ?: bumpPatchVersion(currentVersion)

    println("📌 Bumping version from $currentVersion to $newVersion")

    updateVersionInFile(rootProject.file("ogiri-core/build.gradle.kts"), currentVersion, newVersion)
    updateVersionInFile(
        rootProject.file("sample/sample-java/build.gradle.kts"), currentVersion, newVersion)
    updateVersionInFile(
        rootProject.file("sample/sample-kotlin/build.gradle.kts"), currentVersion, newVersion)

    println("✅ Version updated successfully")
  }
}

/**
 * Bumps the patch component of a semantic version string and returns the resulting snapshot
 * version.
 *
 * If the input ends with "-SNAPSHOT" that suffix is ignored for parsing. For versions with at least
 * three dot-separated segments the patch segment is incremented and "-SNAPSHOT" is appended. For
 * shorter versions ".1-SNAPSHOT" is appended to the sanitized input.
 *
 * @param version The version string to bump (may include a trailing "-SNAPSHOT").
 * @return The new version string with a trailing "-SNAPSHOT".
 * @throws IllegalArgumentException if the version has a third segment that cannot be parsed as an
 *   integer.
 */
fun bumpPatchVersion(version: String): String {
  val sanitized = version.replace("-SNAPSHOT", "")
  val parts = sanitized.split(".")
  return if (parts.size >= 3) {
    val patch =
        parts[2].toIntOrNull()
            ?: throw IllegalArgumentException(
                "Invalid version format for auto-bumping: '$version'. " +
                    "Patch segment '${parts[2]}' is not a number. Please provide newVersion manually.")
    "${parts[0]}.${parts[1]}.${patch + 1}-SNAPSHOT"
  } else {
    "$sanitized.1-SNAPSHOT"
  }
}

/**
 * Updates occurrences of a specific version assignment in the given file to a new version.
 *
 * Matches lines of the form `version = "x.y.z"` or `project.version = 'x.y.z'` (allowing
 * surrounding whitespace), replaces only when the assigned value exactly equals `oldVersion`, and
 * preserves the surrounding quotes and any `project.` prefix. If the file does not exist the
 * function logs a warning and returns without error. If a change is made the file is overwritten
 * and a confirmation is logged.
 *
 * @param file The file to scan and potentially update.
 * @param oldVersion The exact version string to replace (must match the assigned value in the
 *   file).
 * @param newVersion The version string to write in place of `oldVersion`.
 */
fun updateVersionInFile(file: File, oldVersion: String, newVersion: String) {
  if (!file.exists()) {
    println("⚠️  File not found: ${file.absolutePath}")
    return
  }

  val content = file.readText()
  val versionRegex =
      Regex("""^(\s*(?:project\.)?version\s*=\s*["'])$oldVersion(["'])""", RegexOption.MULTILINE)
  val updated =
      versionRegex.replace(content) { matchResult ->
        "${matchResult.groupValues[1]}$newVersion${matchResult.groupValues[2]}"
      }

  if (content != updated) {
    file.writeText(updated)
    println("✏️  Updated ${file.name}")
  }
}
