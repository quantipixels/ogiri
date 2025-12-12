# Security Policy

## Reporting Security Vulnerabilities

If you discover a security vulnerability in the **ogiri** project, please report it responsibly. We appreciate your help in improving our security posture.

### How to Report

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please report security vulnerabilities by emailing:
- **Primary Contact:** oluwaseyi@quantipixels.com
- **Subject:** `[SECURITY] Vulnerability Report - ogiri`

Include the following information in your report:
1. **Description** of the vulnerability
2. **Affected Component** (e.g., OgiriTokenService, OgiriTokenAuthenticationFilter, OgiriTokenRepository)
3. **Affected Version(s)** (version tag or commit hash)
4. **Steps to Reproduce** (if possible)
5. **Impact Assessment** (low, medium, high, critical)
6. **Suggested Fix** (if available)

### Response Timeline

We follow this responsible disclosure timeline:
- **24 hours:** Acknowledgment of receipt
- **7 days:** Initial assessment and communication about next steps
- **30 days:** Target for patch development and testing
- **60 days:** Public disclosure (either when patch is released or as agreed)

If you don't receive a response within 24 hours, please follow up via GitHub issue mentioning you have a security concern waiting for response.

---

## Known Security Considerations

### Token Storage

**Important:** This library provides token management, but security depends on proper usage:

1. **Never store plaintext tokens** – Always hash tokens before storing (BCrypt recommended)
2. **Always use HTTPS/TLS** – Token transmission must occur over encrypted channels
3. **Token expiration** – Implement appropriate token TTL based on your security requirements
4. **Token rotation** – Utilize built-in rotation mechanisms with grace periods
5. **Database security** – Ensure your token storage backend is properly secured

### Authentication Header

The `Authorization` header contains token information. Ensure:
- HTTPS is enforced for all requests containing auth headers
- Proxy servers don't log authorization headers
- Client-side code doesn't store tokens in localStorage (use httpOnly cookies)
- CORS policies are properly configured

### Sub-Tokens

If using sub-tokens:
- Implement proper scope validation
- Use appropriate TTLs for each sub-token type
- Monitor sub-token usage patterns
- Revoke sub-tokens promptly when access should be restricted

### Database Access

The library itself doesn't enforce database security. Ensure:
- Database credentials are externalized (environment variables, secrets management)
- Network access to database is restricted
- Regular database backups are performed
- Database audit logging is enabled
- Token table has appropriate indexes for efficient cleanup

---

## Security Best Practices for Users

### Configuration

```yaml
# DO: Use environment variables for sensitive data
spring:
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD:!required}

# DON'T: Hardcode credentials
spring:
  datasource:
    username: postgres
    password: mypassword
```

### Token Rotation

Enable and configure token rotation based on your security requirements:

```yaml
ogiri:
  auth:
    rotate-on-write-only: false  # Rotate on every write
    rotate-stale-seconds: 3600   # Rotate tokens older than 1 hour
    batch-grace-seconds: 30      # Grace period for old tokens
```

### CORS Configuration

Properly configure CORS to prevent unauthorized cross-origin token theft:

```kotlin
@Configuration
class SecurityConfig {
  @Bean
  fun corsConfigurationSource(): CorsConfigurationSource {
    val config = CorsConfiguration().apply {
      allowedOrigins = listOf("https://yourdomain.com")  // Specific origins only
      allowedMethods = listOf("GET", "POST", "PUT", "DELETE")
      allowedHeaders = listOf("*")
      exposedHeaders = listOf("Authorization", "access-token", "sub-tokens")
      allowCredentials = true
      maxAge = 3600
    }
    val source = UrlBasedCorsConfigurationSource()
    source.registerCorsConfiguration("/**", config)
    return source
  }
}
```

### Logging

Be careful with logging to avoid exposing tokens:

```kotlin
// DON'T log tokens
logger.info("User token: $token")

// DO log token identifiers instead
logger.info("User $userId authenticated with token type: $tokenType")
```

---

## Dependency Security

We actively monitor dependencies for security vulnerabilities:

- **Dependabot** is configured to check for dependency updates weekly
- **GitHub Security Scanning** is enabled for vulnerability detection
- **Regular audits** are performed on the dependency tree

To check for vulnerabilities in your copy:
```bash
./gradlew dependencyCheckAnalyze
```

---

## Vulnerability Disclosure

When a security vulnerability is reported and patched:

1. A patch release is created with a security fix
2. CVE is requested if applicable
3. Security advisory is published
4. Release notes clearly indicate the security fix
5. All users are encouraged to upgrade

### Past Security Issues

None reported yet.

---

## Security Features

### What ogiri Provides

✅ Token-based authentication
✅ Token rotation with grace periods
✅ Sub-token isolation
✅ Configurable expiration
✅ Hashed token storage (application-configured)
✅ Filter-based enforcement
✅ Support for multiple databases

### What ogiri Does NOT Provide

❌ Encryption (you control token hashing)
❌ Network security (HTTPS is your responsibility)
❌ Session fixation protection (implement via headers/cookies)
❌ CSRF protection (implement via middleware)
❌ Rate limiting (implement at your application level)
❌ Intrusion detection (implement monitoring separately)

### Recommendations for Complete Security

1. **Use HTTPS everywhere** – All token exchanges must be encrypted
2. **Implement rate limiting** – Prevent token brute-force attacks
3. **Monitor token usage** – Alert on unusual patterns
4. **Regular security audits** – Code and infrastructure reviews
5. **Incident response plan** – Prepare for token compromise scenarios
6. **User education** – Teach users not to share tokens

---

## Contact

For security questions or concerns, contact: oluwaseyi@quantipixels.com

For general support: See [contributing.md](./docs/contributing.md)

---

## Acknowledgments

We thank all security researchers who responsibly report vulnerabilities to help us make ogiri safer for everyone.
