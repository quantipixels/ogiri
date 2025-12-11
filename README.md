# Ògiri

[![Test](https://github.com/mosobande/ogiri/actions/workflows/test.yml/badge.svg)](https://github.com/mosobande/ogiri/actions/workflows/test.yml)
[![Build](https://github.com/mosobande/ogiri/actions/workflows/build.yml/badge.svg)](https://github.com/mosobande/ogiri/actions/workflows/build.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17+-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

Reusable Spring Boot security components for token-based authentication with pluggable sub-tokens.

## Features

- **Database-agnostic** - Works with JPA, MongoDB, Redis, or any custom persistence
- **Auto-configured** - Spring Boot auto-configuration with customization options
- **Token rotation** - Configurable rotation with batch request detection
- **Sub-tokens** - Pluggable sub-tokens for chat, device, API, etc.
- **Secure by default** - BCrypt hashing, timestamp validation, grant-based authorization

## Installation

**Gradle:**
```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.0.1")
```

**Maven:**
```xml
<dependency>
  <groupId>com.quantipixels.ogiri</groupId>
  <artifactId>ogiri-core</artifactId>
  <version>1.0.1</version>
</dependency>
```

**Requirements:** Java 17+, Spring Boot 3.5+

## Quick Start

**1. Implement user directory:**
```kotlin
@Component
class MyUserDirectory(private val userService: UserService) : OgiriUserDirectory {
  override fun findById(id: Long) = userService.getById(id)
  override fun findByUsername(username: String) = userService.getByUsername(username)
  override fun findByEmail(email: String) = userService.getByEmail(email)
  override fun loadUserByUsername(username: String) = userService.getByUsername(username)!!
  override fun recordSuccessfulLogin(userId: Long) { userService.recordLogin(userId) }
}
```

**2. Declare public routes:**
```kotlin
@Component
class MyRouteRegistry : RouteRegistry {
  override fun registrations() = listOf(Route.post("/api/auth/**"), Route.get("/api/health"))
}
```

**3. Issue tokens on login:**
```kotlin
@PostMapping("/api/auth/login")
fun login(@RequestBody request: LoginRequest, response: HttpServletResponse) {
  val user = authenticate(request.username, request.password)
  response.appendAuthHeaders(tokenService.createNewAuthToken(user.id, "web"))
}
```

Done! Ògiri auto-configures the security filter chain.

See the [Quickstart Guide](docs/quickstart.md) for complete examples in both Kotlin and Java.

## Documentation

| Topic | Description |
|-------|-------------|
| [Quickstart](docs/quickstart.md) | 5-minute integration guide |
| [Configuration](docs/configuration.md) | Token rotation, cleanup, batch windows |
| [Database Integration](docs/database.md) | JPA, MongoDB, Redis examples |
| [Sub-tokens](docs/sub-tokens.md) | Device, chat, API tokens |
| [Authentication Flow](docs/authentication.md) | Request lifecycle, headers |
| [Sample Applications](sample/README.md) | Java and Kotlin examples |

## Development

```bash
./gradlew build          # Build and test
./gradlew test           # Run tests only
./gradlew spotlessApply  # Format code
```

See [development.md](docs/development.md) for contributor guidelines.

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
