# Security Policy

## Reporting Security Vulnerabilities

If you discover a security vulnerability in the **ogiri** project, please report it responsibly. We appreciate your help in improving our security posture.

### How to Report

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please report security vulnerabilities by emailing:

- **Primary Contact:** Project Maintainers
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

### Cookie Security

Ogiri supports secure cookie-based authentication with configurable security attributes. Proper cookie configuration is critical to prevent common web vulnerabilities:

**Why Cookie Security Matters:**

1. **`secure: true` (HTTPS-only cookies)**

   - Prevents cookie transmission over unencrypted HTTP connections
   - Protects against network sniffing and man-in-the-middle attacks
   - **REQUIRED for production deployments**
   - The library logs a startup warning if disabled

2. **`http-only: true` (JavaScript-inaccessible cookies)**

   - Prevents client-side JavaScript from accessing auth cookies
   - Mitigates XSS (Cross-Site Scripting) attacks
   - Even if an attacker injects malicious JavaScript, they cannot steal tokens
   - **REQUIRED for production deployments**
   - The library logs a startup warning if disabled

3. **`same-site: Strict` (CSRF protection)**

   - Prevents cookies from being sent with cross-origin requests
   - Mitigates CSRF (Cross-Site Request Forgery) attacks
   - Options: `Strict` (most secure), `Lax` (allows top-level navigation), `None` (requires `secure=true`)
   - **Recommended: `Strict` for APIs, `Lax` for web applications**

4. **`path: "/"` (Cookie scope)**
   - Limits cookie transmission to specific paths
   - Reduces cookie exposure to unrelated endpoints
   - **Recommended: Set to the narrowest path needed** (e.g., `/api` for API-only apps)

**Secure Production Configuration:**

```yaml
ogiri:
  cookies:
    enabled: true
    secure: true # HTTPS-only
    http-only: true # No JavaScript access
    same-site: Strict # CSRF protection
    path: "/" # Adjust to your needs
```

**Development Configuration:**

```yaml
ogiri:
  cookies:
    enabled: true
    secure: false # Allow HTTP in local development
    http-only: true # Keep enabled even in dev
    same-site: Lax # More permissive for testing
    path: "/"
```

**Security Best Practices:**

- **Never disable `http-only` in production** – Even if you think you need JavaScript access, find an alternative approach
- **Always enable `secure` over HTTPS** – Deploy behind a TLS-terminating reverse proxy if needed
- **Use `Strict` SameSite for APIs** – REST/GraphQL APIs typically don't need cross-site requests
- **Use `Lax` SameSite for web apps** – Allows users to navigate to your site from external links
- **Minimize cookie path scope** – If your API is under `/api`, set `path: "/api"`

**Common Mistakes:**

- ❌ Setting `http-only: false` to allow client-side token refresh (use a separate, non-sensitive endpoint instead)
- ❌ Setting `secure: false` in production because you're behind a reverse proxy (configure proxy to pass `X-Forwarded-Proto` header)
- ❌ Using `same-site: None` without understanding CORS implications
- ❌ Setting `path: "/"` when your API is scoped to `/api/*`

**Additional Resources:**

- [OWASP Secure Cookie Attribute](https://owasp.org/www-community/controls/SecureCookieAttribute)
- [OWASP HttpOnly Cookie Flag](https://owasp.org/www-community/HttpOnly)
- [MDN: SameSite cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)

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
    rotate-on-write-only: false # Rotate on every write
    rotate-stale-seconds: 3600 # Rotate tokens older than 1 hour
    batch-grace-seconds: 30 # Grace period for old tokens
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
❌ Rate limiting (optional `OgiriRateLimitHook` SPI available; bring your own implementation)
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

For security questions or concerns, contact the project maintainers.

For general support: See [CONTRIBUTING.md](https://github.com/quantipixels/ogiri/blob/main/CONTRIBUTING.md)

---

## Acknowledgments

We thank all security researchers who responsibly report vulnerabilities to help us make ogiri safer for everyone.
