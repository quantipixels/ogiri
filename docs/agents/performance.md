# Performance Patterns

## Token Prefix Indexing

- **8-char prefix** enables O(1) DB lookups
- Avoids O(n) BCrypt scans across all tokens
- Prefix extracted from raw token before hashing

## Batch Request Caching

- **Recent timestamps cached** to avoid DB queries for batch detection
- Cache key: `userId:client`
- Reduces DB load during concurrent requests

## Token Comparison Caching

- **BCrypt comparison results cached** (Caffeine)
- **TTL**: 1 hour (configurable via `ogiri.cache.expiry-minutes`)
- **Max size**: 10,000 entries (configurable via `ogiri.cache.max-size`)
- Dramatically reduces CPU overhead for repeated validations

## Timing Attack Protection

- `tokensMatch()` enforces a **100ms floor** on token comparison to mask cache hit vs miss timing
- Each auth request holds a servlet thread for at least 100ms during token validation
- **Thread pool sizing**: with a 200-thread pool, max theoretical auth throughput is ~2,000 req/s
- Size `server.tomcat.threads.max` accordingly for auth-heavy workloads

## Conditional Cleanup

- Token cleanup **only runs** when count exceeds 80% of `max-clients`
- Avoids unnecessary database scans

## Batched Deletion

- Cleanup job deletes tokens in **configurable batches**
- **Batch size**: 1,000 (configurable via `ogiri.cleanup.batch-size`)
- Prevents DB overload during bulk deletions

## Sub-token Registry Caching

- Registry lookups **cached at service initialization**
- Avoids repeated Spring context queries

## Configuration

Key performance-related properties:

```yaml
ogiri:
  cache:
    max-size: 10000 # Token equality cache size
    expiry-minutes: 60 # Cache TTL
  cleanup:
    batch-size: 1000 # Deletion batch size
    interval-ms: 21600000 # 6 hours
  auth:
    max-clients: 10 # Triggers cleanup threshold
```
