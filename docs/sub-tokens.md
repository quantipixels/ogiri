# Sub-Tokens

Sub-tokens are specialized tokens issued alongside the main APP token for specific use cases.

## Use Cases

- **Device tokens** - Per-device authentication with shorter expiry
- **Chat tokens** - WebSocket connections with custom scopes
- **API tokens** - Third-party access with restricted permissions
- **Mobile tokens** - Platform-specific handling

## Creating Sub-Tokens

Implement `OgiriSubTokenRegistration`:

=== "Kotlin"

    ```kotlin
    @Bean
    fun deviceSubToken(): OgiriSubTokenRegistration = object : OgiriSubTokenRegistration {
      override val name = "device"
      override val includeByDefault = true  // Issued with every APP token

      override fun clientIdFor(parentClientId: String): String =
        "$parentClientId.device"

      override fun expiry(parentExpiry: Instant): Instant =
        minOf(parentExpiry, Instant.now().plus(12, ChronoUnit.HOURS))
    }
    ```

=== "Java"

    ```java
    @Bean
    public OgiriSubTokenRegistration deviceSubToken() {
      return new OgiriSubTokenRegistration() {
        @Override public String getName() { return "device"; }
        @Override public boolean isIncludeByDefault() { return true; }
        @Override public String clientIdFor(String parentClientId) {
          return parentClientId + ".device";
        }
        @Override public Instant expiry(Instant parentExpiry) {
          Instant limit = Instant.now().plus(Duration.ofHours(12));
          return parentExpiry.isBefore(limit) ? parentExpiry : limit;
        }
      };
    }
    ```

## Sub-Token Properties

| Property           | Type     | Description                                 |
| ------------------ | -------- | ------------------------------------------- |
| `name`             | String   | Unique identifier ("device", "chat", "api") |
| `includeByDefault` | Boolean  | Auto-issue with every APP token             |
| `clientIdFor()`    | Function | Derive sub-token client ID from parent      |
| `expiry()`         | Function | Calculate expiry from parent expiry         |
| `validate()`       | Function | Optional custom validation logic            |

## Header Format

Sub-tokens are returned in the `sub-tokens` header as Base64-encoded JSON:

```
sub-tokens: eyJkZXZpY2UiOnsiY2xpZW50IjoiYXBwLmRldmljZSIsInRva2VuIjoiYWJjMTIzIiwiZXhwaXJ5IjoiMjAyNS0xMi0yNVQwMDowMDowMFoifX0=
```

Decoded:
```json
{
  "device": {
    "client": "app.device",
    "token": "abc123",
    "expiry": "2025-12-25T00:00:00Z"
  }
}
```

## Managing Sub-Tokens

### Retrieve

```kotlin
val deviceToken = tokenService.getSubToken(userId, "device")
```

### Renew

```kotlin
val newHeaders = tokenService.renewSubToken(userId, "app", "device")
newHeaders?.let { response.appendAuthHeaders(it) }
```

### Revoke

```kotlin
tokenService.revokeSubToken(userId, "device")
```

## Custom Validation

Override `validate()` for custom token format validation:

```kotlin
@Bean
fun xmppSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "xmpp"
  override val includeByDefault = false

  override fun clientIdFor(parentClientId: String) = "$parentClientId.xmpp"
  override fun expiry(parentExpiry: Instant) = Instant.now().plus(4, ChronoUnit.HOURS)

  override fun validate(plainToken: String): Boolean {
    return plainToken.length >= 32 && plainToken.matches(Regex("^[a-z0-9]+$"))
  }
}
```

## Client-Side Usage

### JavaScript

```javascript
const response = await fetch('/api/auth/login', { method: 'POST', body: ... });

const subTokensBase64 = response.headers.get('sub-tokens');
const subTokens = JSON.parse(atob(subTokensBase64));

const deviceToken = subTokens.device.token;
const deviceClient = subTokens.device.client;
```

### Sending Sub-Tokens

```javascript
fetch('/api/device/action', {
  headers: {
    'Authorization': `Bearer ${deviceToken}`,
    'client': deviceClient
  }
});
```

## Rotation Behavior

Sub-tokens follow the APP token lifecycle:

- When `rotate-on-write-only=true`, GET requests don't rotate tokens
- When APP token rotates, sub-tokens with `includeByDefault=true` are recreated
- Use `renewSubToken()` to rotate a sub-token independently

## Examples

### API Token (Opt-In)

```kotlin
@Bean
fun apiSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "api"
  override val includeByDefault = false  // User must request explicitly

  override fun clientIdFor(parentClientId: String) = "$parentClientId.api"
  override fun expiry(parentExpiry: Instant) = Instant.now().plus(7, ChronoUnit.DAYS)
}
```

### Chat Token (Short-Lived)

```kotlin
@Bean
fun chatSubToken(): SubTokenRegistration = object : SubTokenRegistration {
  override val name = "chat"
  override val includeByDefault = true

  override fun clientIdFor(parentClientId: String) = "$parentClientId.chat"
  override fun expiry(parentExpiry: Instant) =
    minOf(parentExpiry, Instant.now().plus(2, ChronoUnit.HOURS))
}
```

## Troubleshooting

| Issue                          | Cause                         | Solution                         |
| ------------------------------ | ----------------------------- | -------------------------------- |
| Sub-tokens not in response     | `includeByDefault = false`    | Request renewal explicitly       |
| Token expires too quickly      | `expiry()` returns early time | Check expiry calculation         |
| Sub-token not found on renewal | Wrong name                    | Verify registration name matches |
| Header parsing fails           | Invalid Base64                | Check `JsonCodec` encoding       |
