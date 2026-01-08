# Quickstart

Get ogiri integrated into your Spring Boot application in 5 minutes.

## 1. Add Dependency

=== "JPA (Recommended)"

    **Gradle (Kotlin DSL):**
    ```kotlin
    implementation("com.quantipixels.ogiri:ogiri-jpa:{{ config.extra.ogiri_version }}")
    ```

    **Gradle (Groovy):**
    ```groovy
    implementation 'com.quantipixels.ogiri:ogiri-jpa:{{ config.extra.ogiri_version }}'
    ```

    **Maven:**
    ```xml
    <dependency>
      <groupId>com.quantipixels.ogiri</groupId>
      <artifactId>ogiri-jpa</artifactId>
      <version>{{ config.extra.ogiri_version }}</version>
    </dependency>
    ```

    Includes `ogiri-core` and `spring-boot-starter-data-jpa` transitively. **Reduces boilerplate by ~70%.**

=== "Core Only"

    **Gradle (Kotlin DSL):**
    ```kotlin
    implementation("com.quantipixels.ogiri:ogiri-core:{{ config.extra.ogiri_version }}")
    ```

    **Gradle (Groovy):**
    ```groovy
    implementation 'com.quantipixels.ogiri:ogiri-core:{{ config.extra.ogiri_version }}'
    ```

    **Maven:**
    ```xml
    <dependency>
      <groupId>com.quantipixels.ogiri</groupId>
      <artifactId>ogiri-core</artifactId>
      <version>{{ config.extra.ogiri_version }}</version>
    </dependency>
    ```

    For MongoDB, Redis, or custom persistence implementations.

## 2. Implement Required Interfaces

Ògiri requires two interfaces to connect with your application's user system and routing.

### OgiriUserDirectory

Connects Ògiri to your user database:

=== "Kotlin"

    ```kotlin
    @Component
    class MyUserDirectory(private val userService: UserService) : OgiriUserDirectory {

      override fun findById(id: Long): OgiriUser? = userService.getById(id)

      override fun findByUsername(username: String): OgiriUser? = userService.getByUsername(username)

      override fun findByEmail(email: String): OgiriUser? = userService.getByEmail(email)

      override fun loadUserByUsername(username: String): OgiriUser =
        userService.getByUsername(username) ?: throw UsernameNotFoundException(username)

      override fun recordSuccessfulLogin(userId: Long) {
        userService.recordLogin(userId)
      }
    }
    ```

=== "Java"

    ```java
    @Component
    public class MyUserDirectory implements OgiriUserDirectory {
      private final UserService userService;

      public MyUserDirectory(UserService userService) {
        this.userService = userService;
      }

      @Override
      public OgiriUser findById(Long id) {
        return userService.getById(id);
      }

      @Override
      public OgiriUser findByUsername(String username) {
        return userService.getByUsername(username);
      }

      @Override
      public OgiriUser findByEmail(String email) {
        return userService.getByEmail(email);
      }

      @Override
      public OgiriUser loadUserByUsername(String username) {
        OgiriUser user = userService.getByUsername(username);
        if (user == null) throw new UsernameNotFoundException(username);
        return user;
      }

      @Override
      public void recordSuccessfulLogin(Long userId) {
        userService.recordLogin(userId);
      }
    }
    ```

### RouteRegistry

Declares which routes bypass authentication:

=== "Kotlin"

    ```kotlin
    @Component
    class MyRouteRegistry : OgiriRouteRegistry {
      override fun registrations() = listOf(
        OgiriRoute.post("/api/auth/login"),
        OgiriRoute.post("/api/auth/register"),
        OgiriRoute.get("/api/health"),
      )
    }
    ```

=== "Java"

    ```java
    @Component
    public class MyRouteRegistry implements OgiriRouteRegistry {
      @Override
      public List<OgiriRoute> registrations() {
        return List.of(
          OgiriRoute.post("/api/auth/login"),
          OgiriRoute.post("/api/auth/register"),
          OgiriRoute.get("/api/health")
        );
      }
    }
    ```

### Token Persistence

If using `ogiri-jpa`, create a simple token entity extending `OgiriBaseTokenEntity`:

=== "Kotlin"

    ```kotlin
    @Entity
    @Table(name = "user_tokens")
    class MyToken : OgiriBaseTokenEntity()
    ```

=== "Java"

    ```java
    @Entity
    @Table(name = "user_tokens")
    public class MyToken extends OgiriBaseTokenEntity {}
    ```

Then create your repository adapter. See [Database Integration](database.md) for the complete setup with JPA, MongoDB, Redis, and custom implementations.

## 3. Issue Tokens on Login

=== "Kotlin"

    ```kotlin
    @RestController
    class AuthController(private val tokenService: OgiriTokenService<Token>) {

      @PostMapping("/api/auth/login")
      fun login(@RequestBody request: LoginRequest, response: HttpServletResponse): ResponseEntity<*> {
        val user = authenticate(request.username, request.password)
        val authHeader = tokenService.createNewAuthToken(user.id, "web")
        response.appendAuthHeaders(authHeader)
        return ResponseEntity.ok(mapOf("message" to "Login successful"))
      }
    }
    ```

=== "Java"

    ```java
    @RestController
    public class AuthController {
      private final OgiriTokenService<Token> tokenService;

      public AuthController(OgiriTokenService<Token> tokenService) {
        this.tokenService = tokenService;
      }

      @PostMapping("/api/auth/login")
      public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        User user = authenticate(request.getUsername(), request.getPassword());
        AuthHeader authHeader = tokenService.createNewAuthToken(user.getId(), "web");
        AuthHeaderKt.appendAuthHeaders(response, authHeader);
        return ResponseEntity.ok(Map.of("message", "Login successful"));
      }
    }
    ```

## Done!

Ògiri auto-configures the security filter chain. Authenticated requests will have their tokens validated and rotated automatically.

**Response headers after login:**
```
access-token: <token-hash>
client: web
uid: 123
expiry: 2025-12-25T00:00:00Z
```

**Client sends on subsequent requests:**
```
access-token: <token-hash>
client: web
uid: 123
expiry: 2025-12-25T00:00:00Z
```

## Next Steps

| Topic                                                                      | Description                                      |
| -------------------------------------------------------------------------- | ------------------------------------------------ |
| [Configuration](configuration.md)                                          | Token rotation, cleanup schedules, batch windows |
| [Database Integration](database.md)                                        | JPA, MongoDB, Redis, custom implementations      |
| [Sub-tokens](sub-tokens.md)                                                | Device tokens, chat tokens, API tokens           |
| [Authentication Flow](authentication.md)                                   | Request lifecycle, rotation policies, headers    |
| [Sample Applications](https://github.com/mosobande/ogiri/tree/main/sample) | Complete Java and Kotlin examples                |
