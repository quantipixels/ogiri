import com.diffplug.gradle.spotless.SpotlessExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  // JPA plugin removed - library is now database-agnostic
  // allopen plugin removed - not needed without JPA
  id("io.spring.dependency-management") version libs.versions.dependencyManagement.get()
  id("com.diffplug.spotless") version libs.versions.spotless.get()
  jacoco
  `maven-publish`
  signing
}

group = "com.quantipixels.ogiri"

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
  api("org.springframework.boot:spring-boot-starter-validation")
  api("org.springframework:spring-tx")
  // spring-boot-starter-data-jpa removed - users choose their own persistence
  // Users must provide one of:
  // - spring-boot-starter-data-jpa (for JPA/Hibernate)
  // - spring-boot-starter-data-mongodb (for MongoDB)
  // - Custom JDBC implementation with JdbcTemplate
  // - Other persistence mechanism of choice
  implementation("org.springframework.boot:spring-boot-autoconfigure")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(module = "mockito-core")
  }
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

/**
 * Test configuration with JaCoCo code coverage reporting. Run tests: ./gradlew test Generate
 * coverage report: ./gradlew jacocoTestReport View coverage: open
 * build/reports/jacoco/test/html/index.html
 */
tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required = true
    csv.required = false
    html.required = true
    html.outputLocation = layout.buildDirectory.dir("reports/jacoco/test/html")
  }
}

/**
 * JaCoCo code coverage configuration. Enforces minimum 50% coverage for critical token
 * functionality.
 */
jacoco { toolVersion = "0.8.11" }

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      pom {
        name.set("ogiri")
        description.set(
            "Spring Boot token security components with auth headers, filters, and sub-token support.")
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
      val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
      val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
      url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
      credentials {
        username = (findProperty("ossrhUsername") ?: System.getenv("OSSRH_USERNAME"))?.toString()
        password = (findProperty("ossrhPassword") ?: System.getenv("OSSRH_PASSWORD"))?.toString()
      }
    }
  }
}

signing {
  val hasGpgKey = findProperty("signing.keyId") != null || System.getenv("GPG_KEY_ID") != null
  if (hasGpgKey) {
    val pub = publishing.publications.findByName("mavenJava")
    if (pub != null) {
      sign(pub)
    }
  }
}

// Configure formatters
configure<SpotlessExtension> {
  kotlin {
    target("src/**/*.kt", "src/**/*.kts")
    targetExclude("**/build.gradle.kts", "**/settings.gradle.kts", "**/spotless.license.kt")
    licenseHeaderFile(rootProject.file("spotless.license.kt"))
    ktfmt("0.43")
    trimTrailingWhitespace()
    endWithNewline()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt("0.43")
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
