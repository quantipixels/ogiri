# Ògiri

[![Test](https://github.com/quantipixels/ogiri/actions/workflows/test.yml/badge.svg)](https://github.com/quantipixels/ogiri/actions/workflows/test.yml)
[![Build](https://github.com/quantipixels/ogiri/actions/workflows/build.yml/badge.svg)](https://github.com/quantipixels/ogiri/actions/workflows/build.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17+-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

Reusable Spring Boot security components for token-based authentication with pluggable sub-tokens.

**[📖 Full Documentation](https://quantipixels.github.io/ogiri/)** | [Quickstart](https://quantipixels.github.io/ogiri/quickstart/) | [Migration Guide](https://quantipixels.github.io/ogiri/migration-guide/)

## Features

- **Database-agnostic** - Works with JPA, MongoDB, Redis, or any custom persistence
- **Auto-configured** - Spring Boot auto-configuration with customization options
- **Token rotation** - Configurable rotation with batch request detection
- **Sub-tokens** - Pluggable sub-tokens for chat, device, API, etc.
- **Secure by default** - BCrypt hashing, timestamp validation, grant-based authorization

## Installation

### Server (Kotlin/Java)

**With JPA Support (Recommended):**

```kotlin
implementation("com.quantipixels.ogiri:ogiri-jpa:1.4.1")
```

**Core Only (Custom Persistence):**

```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.4.1")
```

**Maven (JPA):**

```xml
<dependency>
  <groupId>com.quantipixels.ogiri</groupId>
  <artifactId>ogiri-jpa</artifactId>
  <version>1.4.1</version>
</dependency>
```

> **Note:** Replace `LATEST` with the version found in [.ogiri-version](./.ogiri-version) or on [Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core).

**Requirements:** Java 17+, Spring Boot 3.5+

### Client (TypeScript/JavaScript)

**TypeScript/JavaScript client for browser and Node.js:**

```bash
npm install ogiri-security-client
# or
pnpm add ogiri-security-client
```

The TypeScript client handles token storage, automatic rotation, and request interception. See [ogiri-client/](ogiri-client/) for usage examples and API documentation.

> **Note:** TypeScript client is currently in development (v0.1.0). Package not yet published to npm.

## Quick Start

**1. Implement user directory:**

Connect your user database by implementing `OgiriUserDirectory`. Note that `loadUserByUsername` is inherited from Spring Security's `UserDetailsService` and must throw `UsernameNotFoundException` if the user does not exist.

```kotlin
@Component
class MyUserDirectory(private val userService: UserService) : OgiriUserDirectory {
  override fun findById(id: Long): OgiriUser? = userService.getById(id)
  override fun findByUsername(username: String): OgiriUser? = userService.getByUsername(username)
  override fun findByEmail(email: String): OgiriUser? = userService.getByEmail(email)

  override fun loadUserByUsername(username: String): OgiriUser =
      userService.getByUsername(username) ?: throw UsernameNotFoundException("User not found: $username")

  override fun recordSuccessfulLogin(userId: Long) { userService.recordLogin(userId) }
}
```

**2. Declare public routes:**

```kotlin
@Component
class MyRouteRegistry : OgiriRouteRegistry {
  override fun routes() = listOf(OgiriRoute.post("/api/auth/**"), OgiriRoute.get("/api/health"))
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

See the [full Quickstart Guide](https://quantipixels.github.io/ogiri/quickstart/) for complete examples in both Kotlin and Java.

## Documentation

| Topic                                                                                     | Description                                |
| ----------------------------------------------------------------------------------------- | ------------------------------------------ |
| [Quickstart](https://quantipixels.github.io/ogiri/quickstart/)                            | 5-minute integration guide                 |
| [Interface Design](https://quantipixels.github.io/ogiri/core-concepts/interface-design/)  | Architecture and design philosophy         |
| [Configuration](https://quantipixels.github.io/ogiri/guides/configuration/)               | Token rotation, cleanup, batch windows     |
| [Database Integration](https://quantipixels.github.io/ogiri/guides/database-integration/) | JPA, MongoDB, Redis examples               |
| [Sub-tokens](https://quantipixels.github.io/ogiri/guides/sub-tokens/)                     | Device, chat, API tokens                   |
| [Authentication Flow](https://quantipixels.github.io/ogiri/guides/authentication-flow/)   | Request lifecycle, headers                 |
| [Migration Guide](https://quantipixels.github.io/ogiri/guides/migration-guide/)           | Upgrade guide (see docs for version notes) |
| [Sample Applications](https://github.com/quantipixels/ogiri/tree/main/sample)             | Java and Kotlin examples                   |

## Development

```bash
./gradlew build          # Build and test
./gradlew test           # Run tests only
./gradlew spotlessApply  # Format code

# Enable git hooks (optional)
./scripts/install.sh
```

See [development guide](https://quantipixels.github.io/ogiri/contributing/development-guide/) for contributor guidelines.

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.
