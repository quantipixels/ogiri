# Sub-Tokens Guide

Sub-tokens enable issuance of protocol-specific or use-case-specific tokens alongside the primary APP token. This guide covers creating, managing, and using sub-tokens in your ogiri-secured application.

## Overview

Sub-tokens allow you to issue specialized tokens for different purposes:

- **Device tokens** – One per device, shorter expiry, device-specific client IDs
- **Chat tokens** – For real-time chat connections (WebSocket), custom scopes
- **API tokens** – For third-party API access, restricted permissions
- **Mobile tokens** – For mobile app-specific flows, platform-specific handling

Each sub-token:
- Has its own client ID (derived from parent client)
- Can have independent expiry logic
- Inherits user association from parent APP token
- Is serialized in the `sub-tokens` HTTP header as Base64-encoded JSON

## Architecture

### Registration

Sub-tokens are registered via `SubTokenRegistration` beans:

```kotlin
interface SubTokenRegistration {
  val name: String                           // Unique name: "device", "chat", "api"
  val includeByDefault: Boolean              // Issued with every new APP token?
  fun clientIdFor(parentClientId: String): String  // Derive sub-token client ID
  fun expiry(parentExpiry: Instant): Instant       // Calculate sub-token expiry
}
```

### Sub-Token Lifecycle

1. **Registration** – App provides `SubTokenRegistration` bean during startup
2. **Discovery** – `OgiriSecurityAutoConfiguration` collects all registered sub-tokens
3. **Issuance** – When creating new APP token:
   - If `includeByDefault = true`, sub-token created automatically
   - Stored in database as token entity with `tokenType = "device"` (etc.)
4. **Validation** – Filter validates both APP and sub-tokens on each request
5. **Renewal** – Explicit renewal via `TokenService.renewSubToken()` or automatic on rotation

### Header Format

Sub-tokens are serialized as Base64-encoded JSON in the `sub-tokens` response header:

```
sub-tokens: eyJkZXZpY2UiOnsiY2xpZW50IjoiYXBwLWRldmljZSIsInRva2VuIjoiaGFzaGVkLXRva2VuIiwiZXhwaXJ5IjoiMjAyNS0xMi0yMFQxMjowMDowMFoifSwic3RhZ2luZyI6eyJjbGllbnQiOiJhcHAuc3RhZ2luZyIsInRva2VuIjoiaGFzaGVkLXRva2VuMiIsImV4cGlyeSI6IjIwMjUtMTItMDhUMTI6MDA6MDBaIn19
```

Decoded:

```json
{
  "device": {
    "client": "app.device",
    "token": "hashed-token",
    "expiry": "2025-12-20T12:00:00Z"
  },
  "staging": {
    "client": "app.staging",
    "token": "hashed-token2",
    "expiry": "2025-12-08T12:00:00Z"
  }
}
```

## Creating Sub-Tokens

### Basic Example: Device Token

Register a sub-token that expires 12 hours before the parent APP token:

```kotlin
@Configuration
class SubTokenConfig {
  @Bean
  fun deviceSubToken(): SubTokenRegistration = object : SubTokenRegistration {
    override val name = "device"
    override val includeByDefault = true  // Issued with every APP token

    override fun clientIdFor(parentClientId: String): String =
      "$parentClientId.device"  // "app" → "app.device"

    override fun expiry(parentExpiry: Instant): Instant =
      minOf(parentExpiry, Instant.now().plus(12, ChronoUnit.HOURS))
  }
}
```

**Java:**

```java
@Configuration
public class SubTokenConfig {
  @Bean
  public SubTokenRegistration deviceSubToken() {
    return new SubTokenRegistration() {
      @Override public String getName() { return "device"; }
      @Override public boolean isIncludeByDefault() { return true; }
      @Override public String clientIdFor(String parentClientId) {
        return parentClientId + ".device";
      }
      @Override public Instant expiry(Instant parentExpiry) {
        Instant twelveHoursLater = Instant.now().plus(Duration.ofHours(12));
        return parentExpiry.isBefore(twelveHoursLater) ? parentExpiry : twelveHoursLater;
      }
    };
  }
}
```

### Example: API Token (Opt-In)

Tokens not auto-issued; clients request renewal explicitly:

```kotlin
@Bean
fun apiSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "api"
  override val includeByDefault = false  // Not issued automatically

  override fun clientIdFor(parentClientId: String): String =
    "$parentClientId.api"

  override fun expiry(parentExpiry: Instant): Instant =
    Instant.now().plus(7, ChronoUnit.DAYS)  // 7-day API token
}
```

### Example: Chat Token (WebSocket)

Shorter-lived token for real-time chat connections:

```kotlin
@Bean
fun chatSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "chat"
  override val includeByDefault = true

  override fun clientIdFor(parentClientId: String): String =
    "$parentClientId.chat"

  override fun expiry(parentExpiry: Instant): Instant =
    minOf(parentExpiry, Instant.now().plus(2, ChronoUnit.HOURS))
}
```

## Using Sub-Tokens

### Creating Tokens with Sub-Tokens

When you call `tokenService.createNewAuthToken()`, all `includeByDefault` sub-tokens are automatically created:

```kotlin
@RestController
class AuthController(private val tokenService: TokenService<Token>) {
  @PostMapping("/api/auth/login")
  fun login(@RequestBody request: LoginRequest, response: HttpServletResponse) {
    val user = authenticate(request.username, request.password)
    val authHeader = tokenService.createNewAuthToken(user.id, "app")

    // Response headers:
    // access-token: <hash>
    // client: app
    // uid: 1
    // expiry: 2025-12-20T12:00:00Z
    // sub-tokens: eyJkZXZpY2UiOnt...fX0= (base64 encoded)

    response.appendAuthHeaders(authHeader)
    return mapOf("message" to "Login successful")
  }
}
```

### Renewing a Sub-Token On-Demand

Renew a specific sub-token without rotating the APP token:

```kotlin
@PostMapping("/api/auth/refresh-device-token")
fun refreshDeviceToken(
  @RequestHeader("uid") userId: Long,
  response: HttpServletResponse
) {
  val authHeader = tokenService.renewSubToken(userId, "app", "device")
  response.appendAuthHeaders(authHeader)  // Only device token is updated
  return mapOf("message" to "Device token renewed")
}
```

### Client-Side: Extracting Sub-Tokens

After login, parse the `sub-tokens` header:

**JavaScript/TypeScript:**

```typescript
const response = await fetch('/api/auth/login', {
  method: 'POST',
  body: JSON.stringify({ username, password })
});

const subTokensBase64 = response.headers.get('sub-tokens');
const subTokensJson = atob(subTokensBase64);
const subTokens = JSON.parse(subTokensJson);

// Use device token for device-specific operations
const deviceToken = subTokens.device.token;
const deviceClient = subTokens.device.client;

// Use chat token for WebSocket connection
const chatToken = subTokens.chat.token;
const chatClient = subTokens.chat.client;
```

**Python:**

```python
import base64
import json

response = requests.post('/api/auth/login', json={'username': username, 'password': password})

sub_tokens_base64 = response.headers.get('sub-tokens')
sub_tokens = json.loads(base64.b64decode(sub_tokens_base64))

device_token = sub_tokens['device']['token']
device_client = sub_tokens['device']['client']
```

### Sending Sub-Tokens in Subsequent Requests

For API calls requiring a specific sub-token, include it in headers:

```kotlin
// Device-specific operation
val deviceSubToken = subTokens["device"]
val headers = mapOf(
  "Authorization" to "Bearer ${deviceSubToken.token}",
  "client" to deviceSubToken.client
)
```

## Advanced Scenarios

### Multi-Tenant Sub-Tokens

Issue tenant-specific sub-tokens with restricted permissions:

```kotlin
@Bean
fun tenantSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "tenant"
  override val includeByDefault = false  // User requests specific tenant

  override fun clientIdFor(parentClientId: String): String =
    "$parentClientId.tenant"  // Will vary per user

  override fun expiry(parentExpiry: Instant): Instant =
    minOf(parentExpiry, Instant.now().plus(30, ChronoUnit.DAYS))
}
```

In your auth logic:

```kotlin
@PostMapping("/api/auth/switch-tenant/{tenantId}")
fun switchTenant(
  @PathVariable tenantId: Long,
  @RequestHeader("uid") userId: Long,
  response: HttpServletResponse
) {
  // Verify user has access to tenant
  tokenService.renewSubToken(
    userId = userId,
    parentClientId = "app",
    subTokenName = "tenant"  // Routed to tenantSubToken bean
  )
  response.appendAuthHeaders(authHeader)
}
```

### Platform-Specific Sub-Tokens

Different expiry for mobile vs. web:

```kotlin
@Bean
fun mobileSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "mobile"
  override val includeByDefault = false
  override fun clientIdFor(parentClientId: String) = "$parentClientId.mobile"
  override fun expiry(parentExpiry: Instant) =
    Instant.now().plus(30, ChronoUnit.DAYS)  // Longer for mobile
}

@Bean
fun webSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "web"
  override val includeByDefault = false
  override fun clientIdFor(parentClientId: String) = "$parentClientId.web"
  override fun expiry(parentExpiry: Instant) =
    Instant.now().plus(7, ChronoUnit.DAYS)   // Shorter for web
}
```

## Testing Sub-Tokens

### Unit Test Example

```kotlin
@Test
fun `should create device sub-token with correct expiry`() {
  // Given
  val userId = 1L
  val parentExpiry = Instant.now().plus(14, ChronoUnit.DAYS)

  // When
  val authHeader = tokenService.createNewAuthToken(userId, "app")

  // Then
  assertThat(authHeader.subTokens).containsKey("device")
  val deviceToken = authHeader.subTokens["device"]
  assertThat(deviceToken.expiry).isBefore(parentExpiry.plus(1, ChronoUnit.HOURS))
}
```

### Integration Test Example

```kotlin
@SpringBootTest
class SubTokenIntegrationTest {
  @Test
  fun `should renew chat token independently`() {
    // Login and get initial tokens
    val loginResponse = mockMvc.post("/api/auth/login") { ... }
    val initialSubTokens = parseSubTokensHeader(loginResponse)
    val initialChatToken = initialSubTokens["chat"].token

    // Renew chat token only
    val renewResponse = mockMvc.post("/api/auth/refresh-chat-token") { ... }
    val renewedSubTokens = parseSubTokensHeader(renewResponse)
    val renewedChatToken = renewedSubTokens["chat"].token

    // Chat token changed, others unchanged
    assertThat(renewedChatToken).isNotEqualTo(initialChatToken)
  }
}
```

## Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| Sub-tokens not in response | `includeByDefault = false` or bean not registered | Ensure bean is in Spring context, check `@Bean` annotation |
| Sub-token expires too quickly | `expiry()` returns too-early instant | Review expiry calculation logic, ensure it's after current time |
| Sub-token not found on renewal | Wrong `subTokenName` parameter | Verify sub-token registration name matches exactly |
| Header parsing fails | Invalid Base64 encoding | Check `JsonCodec` is used for encoding, validate with online Base64 decoder |

## References

- **[AUTHENTICATION.md](./AUTHENTICATION.md)** – Token rotation and lifecycle
- **[CONFIGURATION.md](./CONFIGURATION.md)** – Configuration properties
- **[README.md](../README.md)** – Quick start examples
