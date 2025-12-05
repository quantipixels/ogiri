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
rootProject.name = "ogiri"

/*
 * Centralized version management.
 *
 * This is the single source of truth for the project version.
 * Version determination order:
 *   1. Git tag (v1.0.1, v1.0.2, etc.)
 *   2. Environment variable RELEASE_VERSION
 *   3. Default version below
 *
 * To set version:
 *   - Tag: git tag v1.0.1 && git push origin v1.0.1
 *   - Env:  export RELEASE_VERSION=1.0.1
 *   - Or:   gradle -PRELEASE_VERSION=1.0.1 build
 */
val projectVersion = System.getenv("RELEASE_VERSION")
  ?.takeIf { it.isNotBlank() }
  ?: (System.getProperty("RELEASE_VERSION")?.takeIf { it.isNotBlank() })
  ?: "1.0.1"

include(":ogiri-core")
include(":sample:sample-java")
include(":sample:sample-kotlin")

/*
 * Plugin repositories:
 * Where Gradle downloads plugins declared in plugins {} blocks of build scripts.
 * These repos apply only to plugin lookup, NOT library dependencies.
 */
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

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
  repositories {
    mavenCentral()
  }

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
      version("kotlin", "2.0.21")
      version("spotless", "8.0.0")
      version("springBoot", "3.5.7")
      version("dependencyManagement", "1.1.7")
      version("versionsPlugin", "0.52.0")
    }
  }
}
