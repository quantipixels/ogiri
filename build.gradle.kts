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
import com.diffplug.gradle.spotless.SpotlessExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm") version libs.versions.kotlin.get()
  kotlin("plugin.spring") version libs.versions.kotlin.get()
  kotlin("plugin.jpa") version libs.versions.kotlin.get()
  kotlin("plugin.allopen") version libs.versions.kotlin.get()
  id("io.spring.dependency-management") version libs.versions.dependencyManagement.get()
  id("com.diffplug.spotless") version libs.versions.spotless.get()
  `maven-publish`
  signing
}

group = "com.quantipixels.ogiri"
version = "0.1.0-SNAPSHOT"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
  toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
  withSourcesJar()
  withJavadocJar()
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
  jvmToolchain(17)
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
  }
}

dependencies {
  api("org.springframework.boot:spring-boot-starter-security")
  api("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(module = "mockito-core")
  }
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      pom {
        name.set("ogiri")
        description.set("Spring Boot token security components with auth headers, filters, and sub-token support.")
        url.set("https://github.com/quantipixels/ogiri")
        licenses {
          license {
            name.set("Apache License 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0")
          }
        }
        developers {
          developer {
            id.set("quantipixels")
            name.set("Olúwaṣèyí Ṣóbandé")
            email.set("oluwaseyi@quantipixels.com")
          }
        }
        scm {
          url.set("https://github.com/quantipixels/ogiri")
          connection.set("scm:git:https://github.com/quantipixels/ogiri.git")
          developerConnection.set("scm:git:ssh://git@github.com/quantipixels/ogiri.git")
        }
      }
    }
  }
  repositories {
    maven {
      name = "OSSRH"
      val releasesUrl =
        uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
      val snapshotsUrl =
        uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
      url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
      credentials {
        username = (findProperty("ossrhUsername") ?: System.getenv("OSSRH_USERNAME"))?.toString()
        password = (findProperty("ossrhPassword") ?: System.getenv("OSSRH_PASSWORD"))?.toString()
      }
    }
  }
}

signing {
  val pub = publishing.publications.findByName("mavenJava")
  if (pub != null) {
    sign(pub)
  }
}

// Configure formatters
configure<SpotlessExtension> {
  kotlin {
    target("src/**/*.kt", "src/**/*.kts")
    targetExclude("**/build.gradle.kts", "**/settings.gradle.kts", "**/spotless.license.kt")
    licenseHeaderFile("spotless.license.kt")
    ktlint("1.2.1")
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts")
    licenseHeaderFile("spotless.license.kt", "")
    ktlint("1.2.1")
    trimTrailingWhitespace()
    endWithNewline()
  }
  format("misc") {
    target("*.md", ".gitignore", "*.gradle.kts", ".gitattributes")
    trimTrailingWhitespace()
    endWithNewline()
  }
  sql {
    target("src/**/*.sql")
    dbeaver()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
