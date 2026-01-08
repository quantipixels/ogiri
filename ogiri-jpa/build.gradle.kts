import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm")
  kotlin("plugin.spring")
  kotlin("plugin.jpa")
  id("io.spring.dependency-management") version libs.versions.dependencyManagement.get()
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
  api(project(":ogiri-core"))
  api("org.springframework.boot:spring-boot-starter-data-jpa")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(module = "mockito-core")
  }
  testImplementation("com.h2database:h2")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      from(components["java"])
      pom {
        name.set("ogiri-jpa")
        description.set("JPA adapter module for Ogiri token security library.")
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
