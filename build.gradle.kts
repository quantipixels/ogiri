/*
 * Copyright (c) 2025 Quanti Pixels
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

// Apply centralized version management
apply(from = "gradle/version.gradle.kts")

// Consolidate Kotlin plugin in root to avoid loading multiple times in subprojects
plugins {
  kotlin("jvm") version "2.0.21" apply false
  kotlin("plugin.spring") version "2.0.21" apply false
  kotlin("plugin.jpa") version "2.0.21" apply false
}

subprojects {
  repositories {
    mavenCentral()
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

