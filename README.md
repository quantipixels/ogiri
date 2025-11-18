# ogiri

Reusable Spring Boot security components for token-based auth with pluggable sub-tokens.

- Provides Token entity/repository/service, auth header helpers, route catalog, and a
  OncePerRequestFilter (`OgiriTokenAuthenticationFilter`) wired via auto-configuration.
- Supports generic sub-tokens (e.g., chat/device) registered by the hosting app through `SubTokenRegistration`.

## Database schema / migrations

The library does not run DDL automatically. Use the bundled default schema to seed your migration
tool:

- Default Postgres schema: `classpath:/ogiri/db/ogiri-user-tokens.sql`
- Flyway example:
  ```yaml
  spring:
    flyway:
      locations: classpath:db/migration,classpath:/ogiri/db
  ```
- Liquibase example (`db.changelog-master.yaml`):
  ```yaml
  databaseChangeLog:
    - sqlFile:
        path: classpath:/ogiri/db/ogiri-user-tokens.sql
        relativeToChangelogFile: false
  ```
- The SQL includes indexes and a unique `(user_id, client_id)` constraint; uncomment and adjust the
  foreign key in the file if you want to enforce a reference to your users table.

## Adding to a project

1. Include the module as a project dependency (already wired in this repo):
   ```kotlin
   implementation(project(":ogiri"))
   ```
2. Ensure component scanning picks up `com.quantipixels.ogiri.*` (the app’s `@SpringBootApplication`
   can include that package).

## Required adapters in the host app

- `TokenUserDirectory`: load users by id/email/username and record successful logins.
- `RouteRegistry`: expose public/auth routes so the filter can allow unauthenticated paths.
- (Optional) `SubTokenRegistration` beans to define extra sub-tokens.

Example TokenUserDirectory:
```kotlin
@Component
class TokenUserDirectoryAdapter(private val userService: UserService) : TokenUserDirectory {
  override fun loadUserByUsername(username: String) = userService.loadUserByUsername(username)
  override fun findById(id: Long) = userService.getById(id)
  override fun findByEmail(email: String) = userService.getByEmail(email)
  override fun findByPublicId(publicId: String) = userService.getByCircleId(publicId)
  override fun recordSuccessfulLogin(userId: Long) { userService.getById(userId)?.let { userService.save(it.apply { updateLastLoginAt() }) } }
}
```

## Defining sub-tokens

Implement `SubTokenRegistration` to mint additional tokens alongside APP tokens.
Example: sub-token mirrored from this app:
```kotlin
@Configuration
class DeviceSubTokenConfig {
  @Bean
  fun deviceSubTokenRegistration(): SubTokenRegistration =
      object : SubTokenRegistration {
        override val name = "device"
        override fun clientIdFor(parentClientId: String) = "$parentClientId.device"
        override fun expiry(parentExpiry: Instant) = minOf(parentExpiry, Instant.now().plus(12, ChronoUnit.HOURS))
      }
}
```

During `TokenService.createNewAuthToken`, all registrations with `includeByDefault=true` are issued.
Sub-token renewals can be forced via `TokenService.renewSubToken(userId, request, response, name)`.

Renew a sub-token from a controller:
```kotlin
@PostMapping("/api/auth/sub/{name}/renew")
fun renew(
    @PathVariable name: String,
    request: HttpServletRequest,
    response: HttpServletResponse,
    currentUser: CurrentUser,
) {
  tokenService.renewSubToken(currentUser.id, request, response, name)
}
```

Register multiple sub-tokens:
```kotlin
@Configuration
class SubTokenConfigs {
  @Bean
  fun chat(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "chat"
    override fun clientIdFor(parentClientId: String) = "$parentClientId.chat"
    override fun expiry(parentExpiry: Instant) = minOf(parentExpiry, Instant.now().plus(12, ChronoUnit.HOURS))
  }

  @Bean
  fun device(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "device"
    override val includeByDefault = false // only issued when explicitly requested
    override fun clientIdFor(parentClientId: String) = "$parentClientId.device"
    override fun expiry(parentExpiry: Instant) = parentExpiry // tie to APP expiry
  }
}
```
In this example, `chat` issues automatically with every APP token; `device` is only issued when callers request it via `TokenService.renewSubToken(..., name = "device")`.

## Headers

`AuthHeader` emits:
- Core: `access-token`, `client`, `uid`, `expiry`, `access-token-kind`, and Authorization bearer (Base64 JSON).
- Sub-tokens only: `sub-tokens` header with Base64-encoded JSON map `{name: {client, token, expiry}}`.

## Requirements

- Java 17+ (the build toolchain and compiled targets are pinned to 17).
- Kotlin compiler 2.0.x (see `settings.gradle.kts`), compatible with Spring Boot 3.5.x.

## Spring Boot integration

- Auto-configuration: `OgiriSecurityAutoConfiguration` is registered as a Boot auto-config. By default it wires `TokenService<Token>`, `OgiriTokenAuthenticationFilter`, and a `SecurityFilterChain` that registers the filter before `UsernamePasswordAuthenticationFilter`.
- Disabling the default chain: set `ogiri.security.register-filter=false` and register your own `SecurityFilterChain`, injecting `OgiriTokenAuthenticationFilter` and the provided `AuthenticationEntryPoint`.
- Custom token entity: extend `BaseToken` with your own `@Entity`, implement `TokenRepository<MyToken>`, and provide a `TokenService<MyToken>` bean (you can subclass `TokenService` and override `createTokenEntity` for construction).
- Java example:
  ```java
  @Configuration
  public class SecurityConfig {
    @Bean
    public SecurityFilterChain appSecurity(
        HttpSecurity http,
        OgiriTokenAuthenticationFilter filter,
        AuthenticationEntryPoint entryPoint) throws Exception {
      return http
          .csrf(AbstractHttpConfigurer::disable)
          .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
          .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
          .authorizeHttpRequests(reg -> reg.anyRequest().authenticated())
          .build();
    }
  }
  ```

## Testing notes

- WebMvc slices should exclude the auto-configured filter/auto-config and mock EntityManagerFactory or disable JPA if not needed.
- Full suite: `./gradlew test`.

## Documentation

- Authentication and rotation overview: `docs/authentication.md`
