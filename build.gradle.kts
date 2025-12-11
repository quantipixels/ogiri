import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.kotlin.dsl.version

// Apply centralized version management
apply(from = "gradle/version.gradle.kts")

// Consolidate Kotlin plugin in root to avoid loading multiple times in subprojects
plugins {
  kotlin("jvm") version libs.versions.kotlin.get() apply false
  kotlin("plugin.spring") version libs.versions.kotlin.get() apply false
  kotlin("plugin.jpa") version libs.versions.kotlin.get() apply false
  id("com.diffplug.spotless") version libs.versions.spotless.get() apply false
}

allprojects {
  repositories { mavenCentral() }

  apply(plugin = "com.diffplug.spotless")

  configure<SpotlessExtension> {
    kotlin {
      target("src/**/*.kt", "src/**/*.kts")
      targetExclude("**/build.gradle.kts", "**/settings.gradle.kts", "**/spotless.license.kt")
      licenseHeaderFile(rootProject.file("spotless.license.kt"))
      ktfmt("0.43")
      trimTrailingWhitespace()
      endWithNewline()
    }
    java {
      target("src/**/*.java")
      targetExclude("**/build/**")
      licenseHeaderFile(rootProject.file("spotless.license.kt"))
      googleJavaFormat("1.22.0")
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlinGradle {
      target("*.gradle.kts")
      ktfmt("0.43")
      trimTrailingWhitespace()
      endWithNewline()
    }
    sql {
      target("src/**/*.sql")
      dbeaver()
      trimTrailingWhitespace()
      endWithNewline()
    }
    format("misc") {
      target("*.md", ".gitignore", ".gitattributes", "**/*.toml", "**/*.yml", "**/*.yaml")
      targetExclude("**/build/**")
      trimTrailingWhitespace()
      endWithNewline()
    }
  }
}

// Task to install git hooks for development workflow
tasks.register("installGitHooks") {
  description = "Install git pre-commit and pre-push hooks for code quality checks"
  group = "Development"

  doLast {
    val installScript = file("scripts/install.sh")
    if (!installScript.exists()) {
      throw GradleException("install.sh script not found at scripts/install.sh")
    }

    val process = ProcessBuilder("bash", installScript.absolutePath).start()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
      val errorOutput = process.errorStream.bufferedReader().readText()
      throw GradleException("Failed to install git hooks:\n$errorOutput")
    }

    println(process.inputStream.bufferedReader().readText())
  }
}

// Automatically install hooks when setting up the project
tasks.register("setupDev") {
  description = "Set up development environment (installs git hooks)"
  group = "Development"
  dependsOn("installGitHooks")

  doLast {
    println("")
    println("✅ Development environment setup complete!")
    println("")
    println("Your git hooks are installed. They will:")
    println("  • pre-commit:  Check code formatting before allowing commits")
    println("  • pre-push:    Verify tests pass before allowing pushes")
  }
}
