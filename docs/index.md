# Ògiri Security

Ògiri is a Spring Boot security library for token-based authentication with pluggable sub-token support. It handles token issuance, validation, rotation, and cleanup without locking you into a specific database or persistence layer.

## Why Ògiri?

- **Database Freedom** - Use JPA, MongoDB, Redis, or any custom persistence
- **Zero Configuration** - Works out of the box with sensible defaults
- **Flexible Tokens** - Support for sub-tokens (device, chat, API) alongside main tokens
- **Production Ready** - BCrypt hashing, automatic rotation, batch request detection

## Getting Started

Add the dependency and implement two interfaces:

=== "Kotlin"

    ```kotlin
    // 1. Add dependency
    implementation("com.quantipixels.ogiri:ogiri-core:1.2.0")

    // 2. Connect to your user system
    @Component
    class MyUserDirectory(private val userService: UserService) : OgiriUserDirectory {
      override fun findById(id: Long) = userService.getById(id)
      override fun findByUsername(username: String) = userService.getByUsername(username)
      override fun findByEmail(email: String) = userService.getByEmail(email)
      override fun loadUserByUsername(username: String) = userService.getByUsername(username)!!
      override fun recordSuccessfulLogin(userId: Long) { userService.recordLogin(userId) }
    }

    // 3. Declare public routes
    @Component
    class MyRouteRegistry : OgiriRouteRegistry {
      override fun registrations() = listOf(OgiriRoute.post("/api/auth/**"))
    }
    ```

=== "Java"

    ```java
    // 1. Add dependency
    // implementation("com.quantipixels.ogiri:ogiri-core:1.2.0")

    // 2. Connect to your user system
    @Component
    public class MyUserDirectory implements OgiriUserDirectory {
      private final UserService userService;

      public MyUserDirectory(UserService userService) {
        this.userService = userService;
      }

      @Override public OgiriUser findById(Long id) { return userService.getById(id); }
      @Override public OgiriUser findByUsername(String username) { return userService.getByUsername(username); }
      @Override public OgiriUser findByEmail(String email) { return userService.getByEmail(email); }
      @Override public OgiriUser loadUserByUsername(String username) { return userService.getByUsername(username); }
      @Override public void recordSuccessfulLogin(Long userId) { userService.recordLogin(userId); }
    }

    // 3. Declare public routes
    @Component
    public class MyRouteRegistry implements OgiriRouteRegistry {
      @Override
      public List<OgiriRoute> registrations() {
        return List.of(OgiriRoute.post("/api/auth/**"));
      }
    }
    ```

That's it. Ògiri auto-configures the security filter chain.

**[Full Quickstart Guide](quickstart.md)** - Complete setup in 5 minutes with Kotlin and Java examples.

## Documentation

### Getting Started

- [Quickstart](quickstart.md) - Get running in 5 minutes
- [Interface-First Design](interface-first-design.md) - Architecture and design philosophy
- [Implementation Guide](implementation-guide.md) - Complete step-by-step implementation
- [Migration Guide](migration-guide.md) - Upgrading to 1.2.0

### Integration & Configuration

- [Configuration](configuration.md) - All configuration properties
- [Database Integration](database.md) - JPA, MongoDB, Redis, custom adapters
- [Sub-tokens](sub-tokens.md) - Device, chat, API tokens

### Reference

- [Authentication Flow](authentication.md) - Request lifecycle and headers
- [Sample Applications](https://github.com/mosobande/ogiri/tree/main/sample) - Java and Kotlin examples

### Contributing

- [Development Guide](development.md) - Build, test, contribute
- [Contributing Guidelines](contributing.md) - PR process and standards
- [Changelog](changelog.md) - Release history

## Requirements

- Java 17+
- Spring Boot 3.5+
- Your choice of database

## License

Apache License 2.0
