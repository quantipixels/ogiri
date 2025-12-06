# Òǵìrì

[![Test](https://github.com/mosobande/ogiri/actions/workflows/test.yml/badge.svg)](https://github.com/mosobande/ogiri/actions/workflows/test.yml)
[![Build](https://github.com/mosobande/ogiri/actions/workflows/build.yml/badge.svg)](https://github.com/mosobande/ogiri/actions/workflows/build.yml)
[![Lint](https://github.com/mosobande/ogiri/actions/workflows/lint.yml/badge.svg)](https://github.com/mosobande/ogiri/actions/workflows/lint.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.quantipixels.ogiri/ogiri-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/java-17+-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive.html)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.5+-green.svg)](https://spring.io/projects/spring-boot)

Reusable Spring Boot security components for token-based auth with pluggable sub-tokens.

- **Table-agnostic:** Works with any SQL, NoSQL, or custom datastore (JPA, JDBC, MongoDB, Redis, etc.)
- **Token management:** Handles creation, validation, rotation, and cleanup via `TokenService<T>`
- **Sub-tokens:** Support for pluggable sub-tokens (e.g., chat, device, api) registered by your app
- **Auto-configured:** Wired automatically via Spring Boot auto-configuration with customization options
- **Secure by default:** Uses BCrypt hashing, timestamp validation, and grant-based authorization

---

## Installation

### Maven Central

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>com.quantipixels.ogiri</groupId>
  <artifactId>ogiri-core</artifactId>
  <version>1.0.1</version>
</dependency>
```

### Gradle (Maven Central)

Add to your `build.gradle.kts`:

```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.0.1")
```

### JitPack (Git Repository)

For snapshot or development versions directly from git:

**Maven:**
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.mosobande</groupId>
  <artifactId>ogiri</artifactId>
  <version>v1.0.1</version>
</dependency>
```

**Gradle:**
```kotlin
repositories {
  maven { url = uri("https://jitpack.io") }
}

dependencies {
  implementation("com.github.mosobande:ogiri:ori")           // Latest from ori branch
  implementation("com.github.mosobande:ogiri:v1.0.1")       // Release tag (https://jitpack.io/#mosobande/ogiri/v1.0.1)
  implementation("com.github.mosobande:ogiri:abc123def")    // Specific commit
}
```

### Requirements

- **Java 17+** – Pinned to JDK 17
- **Spring Boot 3.5+** – Compatible with latest versions
- **Your choice of persistence** – JPA, JDBC, MongoDB, Redis, or custom

---

## Quick Start

### 1. Implement Required Adapters

**TokenUserDirectory** – Load users from your user store:

*Kotlin*
```kotlin
@Component
class MyTokenUserDirectory(private val userService: UserService) : TokenUserDirectory {
  override fun findById(id: Long) = userService.getById(id)
  override fun findByUsername(username: String) = userService.getByUsername(username)
  override fun findByEmail(email: String) = userService.getByEmail(email)
  override fun loadUserByUsername(username: String) = userService.getByUsername(username)!!
  override fun recordSuccessfulLogin(userId: Long) { userService.recordLogin(userId) }
}
```

*Java*
```java
@Component
public class MyTokenUserDirectory implements TokenUserDirectory {
  private final UserService userService;

  public MyTokenUserDirectory(UserService userService) {
    this.userService = userService;
  }

  @Override
  public User findById(Long id) {
    return userService.getById(id);
  }

  @Override
  public User findByUsername(String username) {
    return userService.getByUsername(username);
  }

  @Override
  public User findByEmail(String email) {
    return userService.getByEmail(email);
  }

  @Override
  public User loadUserByUsername(String username) {
    return userService.getByUsername(username);
  }

  @Override
  public void recordSuccessfulLogin(Long userId) {
    userService.recordLogin(userId);
  }
}
```

**RouteRegistry** – Define public/auth routes:

*Kotlin*
```kotlin
@Component
class MyRouteRegistry : RouteRegistry {
  override fun registrations() = listOf(
    Route.get("/public/**"),
    Route.post("/api/auth/**"),
  )
}
```

*Java*
```java
@Component
public class MyRouteRegistry implements RouteRegistry {
  @Override
  public List<Route> registrations() {
    return List.of(
      Route.get("/public/**"),
      Route.post("/api/auth/**")
    );
  }
}
```

### 2. Set Up Database Persistence

Implement `TokenRepository<T>` for your datastore (JPA, JDBC, MongoDB, etc.):

*Kotlin*
```kotlin
@Repository
interface MyTokenRepository : TokenRepository<MyToken> {
  // Implement the required methods (or use JPA)
}
```

*Java*
```java
@Repository
public interface MyTokenRepository extends TokenRepository<MyToken> {
  // Implement the required methods (or use JPA)
}
```

See **[Database Integration Guide](./docs/DATABASE.md)** for complete examples (JPA, JDBC, MongoDB, Redis).

### 3. Create Tokens

*Kotlin*
```kotlin
@RestController
class AuthController(private val tokenService: TokenService<MyToken>) {
  @PostMapping("/api/auth/login")
  fun login(@RequestBody request: LoginRequest, response: HttpServletResponse) {
    val user = authenticate(request.username, request.password)
    val authHeader = tokenService.createNewAuthToken(user.id, "web-app")
    response.appendAuthHeaders(authHeader)  // Headers: access-token, client, uid, expiry, sub-tokens
    return OK
  }
}
```

*Java*
```java
@RestController
public class AuthController {
  private final TokenService<MyToken> tokenService;

  public AuthController(TokenService<MyToken> tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping("/api/auth/login")
  public void login(@RequestBody LoginRequest request, HttpServletResponse response) {
    User user = authenticate(request.getUsername(), request.getPassword());
    AuthHeader authHeader = tokenService.createNewAuthToken(user.getId(), "web-app");
    response.appendAuthHeaders(authHeader);  // Headers: access-token, client, uid, expiry, sub-tokens
  }
}
```

### 4. (Optional) Register Sub-tokens

*Kotlin*
```kotlin
@Configuration
class SubTokenConfig {
  @Bean
  fun chatSubToken(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "chat"
    override val includeByDefault = true
    override fun clientIdFor(parent: String) = "$parent.chat"
    override fun expiry(parentExpiry: Instant) = parentExpiry  // Same as parent
  }
}
```

*Java*
```java
@Configuration
public class SubTokenConfig {
  @Bean
  public SubTokenRegistration chatSubToken() {
    return new SubTokenRegistration() {
      @Override public String getName() { return "chat"; }
      @Override public boolean isIncludeByDefault() { return true; }
      @Override public String clientIdFor(String parent) { return parent + ".chat"; }
      @Override public Instant expiry(Instant parentExpiry) { return parentExpiry; }
    };
  }
}
```

---

## Configuration & Setup

**For detailed setup instructions, see:**
- **[Configuration Guide](./docs/CONFIGURATION.md)** – Spring properties and auto-config options
- **[Database Integration Guide](./docs/DATABASE.md)** – JPA, JDBC, MongoDB examples
- **[Sub-tokens Guide](./docs/SUB_TOKENS.md)** – Creating and managing sub-tokens

---

## Key Features

| Feature | Details |
|---------|---------|
| **Table-Agnostic** | Works with JPA, JDBC, MongoDB, Redis, DynamoDB, or custom persistence |
| **Token Rotation** | Automatic token rotation with grace period for grace period requests |
| **Sub-tokens** | Support for additional tokens (device, chat, api, etc.) registered by app |
| **Batch Requests** | Detect batch requests within grace period to prevent token thrashing |
| **Token Cleanup** | Scheduled cleanup of expired tokens via `TokenCleanupJob` |
| **Headers** | Automatic serialization of tokens to HTTP headers (access-token, sub-tokens, etc.) |
| **Type-Safe** | Generic `TokenRepository<T>` and `TokenService<T>` for compile-time safety |
| **Pluggable** | Extend or override any component (TokenService, TokenRepository, etc.) |

---

## Database Integration

**Òǵìrì** is database-agnostic. Choose your persistence mechanism:

- **JPA/Hibernate** – Use `@Entity` and `JpaRepository`
- **JDBC** – Implement `TokenRepository<T>` with custom SQL
- **MongoDB** – Use `@Document` and `MongoTemplate`
- **Redis** – Implement `TokenRepository<T>` with Redis client
- **Custom** – Implement `TokenRepository<T>` interface directly

Pre-built schemas are provided for PostgreSQL, MySQL, H2, and MongoDB.

**See [Database Integration Guide](./docs/DATABASE.md) for complete examples.**


## Sample Applications

See `sample/README.md` for minimal Spring Boot examples in Java and Kotlin showing how to integrate **Òǵìrì** with your app.

---

## Building and Testing

```bash
./gradlew build        # Build entire project
./gradlew test         # Run all tests with coverage (JaCoCo)
./gradlew spotlessApply   # Auto-format code
./gradlew :sample:sample-java:bootRun   # Run Java sample
./gradlew :sample:sample-kotlin:bootRun # Run Kotlin sample
```

---

## Version & Release

**Current Version:** 1.0.1

- **Version Management** – See [DEVELOPMENT.md](./DEVELOPMENT.md#version--release) (Version Management section)
- **Release Process** – See [DEVELOPMENT.md](./DEVELOPMENT.md#cicd-pipeline-and-releases) (CI/CD Pipeline and Releases section)

---

## Documentation

| Topic | File |
|-------|------|
| **Configuration & Setup** | [CONFIGURATION.md](./docs/CONFIGURATION.md) (Spring properties, auto-config) |
| **Database Integration** | [DATABASE.md](./docs/DATABASE.md) (JPA, JDBC, MongoDB) |
| **Sub-tokens** | [SUB_TOKENS.md](./docs/SUB_TOKENS.md) (Creating and managing sub-tokens) |
| **Authentication** | [AUTHENTICATION.md](./docs/AUTHENTICATION.md) (Token rotation, headers) |
| **Development** | [DEVELOPMENT.md](./DEVELOPMENT.md) (Architecture, testing strategy, development tasks) |
| **Samples** | [sample/README.md](./sample/README.md) (Java & Kotlin examples) |
