# Quickstart

Get ogiri integrated into your Spring Boot application in 5 minutes.

## 1. Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
implementation("com.quantipixels.ogiri:ogiri-core:1.0.1")
```

**Gradle (Groovy):**
```groovy
implementation 'com.quantipixels.ogiri:ogiri-core:1.0.1'
```

**Maven:**
```xml
<dependency>
  <groupId>com.quantipixels.ogiri</groupId>
  <artifactId>ogiri-core</artifactId>
  <version>1.0.1</version>
</dependency>
```

## 2. Implement Required Interfaces

Ògiri requires two interfaces to connect with your application's user system and routing.

### OgiriUserDirectory

Connects Ògiri to your user database:

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

<details>
<summary>Java version</summary>

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
</details>

### RouteRegistry

Declares which routes bypass authentication:

```kotlin
@Component
class MyRouteRegistry : RouteRegistry {
  override fun registrations() = listOf(
    Route.post("/api/auth/login"),
    Route.post("/api/auth/register"),
    Route.get("/api/health"),
  )
}
```

<details>
<summary>Java version</summary>

```java
@Component
public class MyRouteRegistry implements RouteRegistry {
  @Override
  public List<Route> registrations() {
    return List.of(
      Route.post("/api/auth/login"),
      Route.post("/api/auth/register"),
      Route.get("/api/health")
    );
  }
}
```
</details>

### TokenRepository

Implement token persistence. For JPA:

```kotlin
@Repository
interface MyTokenRepository : JpaRepository<Token, Long>, TokenRepository<Token>
```

<details>
<summary>Java version</summary>

```java
@Repository
public interface MyTokenRepository extends JpaRepository<Token, Long>, TokenRepository<Token> {}
```
</details>

See [Database Integration](database.md) for MongoDB, Redis, and custom implementations.

## 3. Issue Tokens on Login

```kotlin
@RestController
class AuthController(private val tokenService: TokenService<Token>) {

  @PostMapping("/api/auth/login")
  fun login(@RequestBody request: LoginRequest, response: HttpServletResponse): ResponseEntity<*> {
    val user = authenticate(request.username, request.password)
    val authHeader = tokenService.createNewAuthToken(user.id, "web")
    response.appendAuthHeaders(authHeader)
    return ResponseEntity.ok(mapOf("message" to "Login successful"))
  }
}
```

<details>
<summary>Java version</summary>

```java
@RestController
public class AuthController {
  private final TokenService<Token> tokenService;

  public AuthController(TokenService<Token> tokenService) {
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
</details>

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

| Topic | Description |
|-------|-------------|
| [Configuration](configuration.md) | Token rotation, cleanup schedules, batch windows |
| [Database Integration](database.md) | JPA, MongoDB, Redis, custom implementations |
| [Sub-tokens](sub-tokens.md) | Device tokens, chat tokens, API tokens |
| [Authentication Flow](authentication.md) | Request lifecycle, rotation policies, headers |
| [Sample Applications](https://github.com/mosobande/ogiri/tree/main/sample) | Complete Java and Kotlin examples |
