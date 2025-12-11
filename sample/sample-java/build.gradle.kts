plugins {
  java
  id("io.spring.dependency-management") version libs.versions.dependencyManagement.get()
  id("org.springframework.boot") version libs.versions.springBoot.get()
}

group = "com.quantipixels.ogiri.samples"

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
  toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
  }
}

dependencies {
  implementation(project(":ogiri-core"))
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.postgresql:postgresql:42.7.2")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(module = "mockito-core")
  }
  testImplementation("com.h2database:h2:2.2.224")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.bootRun {
  @Suppress("UNCHECKED_CAST")
  systemProperties =
      System.getProperties()
          .stringPropertyNames()
          .associate { it to System.getProperty(it) }
          .toMutableMap() as MutableMap<String, Any>
}
