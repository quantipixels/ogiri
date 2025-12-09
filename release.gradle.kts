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

  doLast {
    val version = project.findProperty("version")?.toString() ?: "0.1.0"
    val isSnapshot = version.endsWith("-SNAPSHOT")

    println("📦 Releasing version: $version")
    println("🔐 Snapshot: $isSnapshot")
    println("✅ Ready to publish to Maven Central")

    // Execute publish task
    project.exec {
      commandLine("./gradlew", "publish")
    }
  }
}

tasks.register("bumpVersion") {
  group = "versioning"
  description = "Bump patch version in all build files"

  doLast {
    val currentVersion = project.findProperty("currentVersion")?.toString()
      ?: System.getenv("CURRENT_VERSION")
      ?: "0.1.0-SNAPSHOT"
    val newVersion = project.findProperty("newVersion")?.toString()
      ?: System.getenv("NEW_VERSION")
      ?: bumpPatchVersion(currentVersion)

    println("📌 Bumping version from $currentVersion to $newVersion")

    updateVersionInFile(rootProject.file("ogiri-core/build.gradle.kts"), currentVersion, newVersion)
    updateVersionInFile(rootProject.file("sample/sample-java/build.gradle.kts"), currentVersion, newVersion)
    updateVersionInFile(rootProject.file("sample/sample-kotlin/build.gradle.kts"), currentVersion, newVersion)

    println("✅ Version updated successfully")
  }
}

fun bumpPatchVersion(version: String): String {
  val sanitized = version.replace("-SNAPSHOT", "")
  val parts = sanitized.split(".")
  return if (parts.size >= 3) {
    "${parts[0]}.${parts[1]}.${parts[2].toInt() + 1}-SNAPSHOT"
  } else {
    "$sanitized.1-SNAPSHOT"
  }
}

fun updateVersionInFile(file: File, oldVersion: String, newVersion: String) {
  if (!file.exists()) {
    println("⚠️  File not found: ${file.absolutePath}")
    return
  }

  val content = file.readText()
  val updated = content.replace(
    oldVersion,
    newVersion
  )

  if (content != updated) {
    file.writeText(updated)
    println("✏️  Updated ${file.name}")
  }
}
