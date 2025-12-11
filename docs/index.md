# Ògiri Security

Ògiri is a Spring Boot security library for token-based authentication with pluggable sub-token support. It handles token issuance, validation, rotation, and cleanup without locking you into a specific database or persistence layer.

## Why Ògiri?

- **Database Freedom** - Use JPA, MongoDB, Redis, or any custom persistence
- **Zero Configuration** - Works out of the box with sensible defaults
- **Flexible Tokens** - Support for sub-tokens (device, chat, API) alongside main tokens
- **Production Ready** - BCrypt hashing, automatic rotation, batch request detection

## Getting Started

Add the dependency and implement two interfaces:

```kotlin
// 1. Add dependency
implementation("com.quantipixels.ogiri:ogiri-core:1.0.1")

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
class MyRouteRegistry : RouteRegistry {
  override fun registrations() = listOf(Route.post("/api/auth/**"))
}
```

That's it. Ògiri auto-configures the security filter chain.

**[Full Quickstart Guide](quickstart.md)** - Complete setup in 5 minutes with Kotlin and Java examples.

## Documentation

### Integration

- [Quickstart](quickstart.md) - Get running in 5 minutes
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
