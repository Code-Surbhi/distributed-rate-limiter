# Rate Limiting Algorithms

This document compares the 4 rate limiting algorithms implemented in this project.

## Quick Comparison

| Algorithm | Time Complexity | Space Complexity | Accuracy | Boundary Attack | Production Use |
|-----------|----------------|------------------|----------|-----------------|----------------|
| Token Bucket | O(1) | O(1) | Good | Resistant | AWS, Stripe, Kong |
| Fixed Window Counter | O(1) | O(1) | Poor | **Vulnerable** | Simple systems |
| Sliding Window Log | O(k)* | O(n)** | Excellent | Resistant | Analytics systems |
| Sliding Window Counter | O(1) | O(1) | Very Good | Resistant | **Cloudflare, Redis** |

*k = number of expired timestamps  
**n = total requests in window

## 1. Token Bucket

### Concept
Tokens are added to a bucket at a constant rate. Each request consumes one token. If the bucket is empty, requests are rejected.

### Pros
- Handles traffic bursts gracefully
- Smooth rate limiting
- Configurable burst size (bucket capacity)

### Cons
- More complex than fixed window
- Requires refill calculation on each request

### Best For
- APIs that allow bursts (e.g., file uploads)
- Systems with variable load patterns

---

## 2. Fixed Window Counter

### Concept
Time is divided into fixed windows. Requests in each window are counted. Counter resets at window boundaries.

### Pros
- Extremely simple
- Minimal memory usage
- Fast O(1) operations

### Cons
- **Boundary Attack**: Users can make 2x requests by exploiting window resets
- Inaccurate during window transitions

### Example Boundary Attack
```
Window 1 (10:00:00-10:00:59): 100 requests at 10:00:59
Window 2 (10:01:00-10:01:59): 100 requests at 10:01:00
Result: 200 requests in 1 second (2x the limit!)
```

### Best For
- Non-critical rate limiting
- Internal APIs with trusted clients

---

## 3. Sliding Window Log

### Concept
Stores timestamp of every request. Removes timestamps older than the window. Counts remaining timestamps.

### Pros
- Most accurate algorithm
- No boundary attack vulnerability
- True sliding window behavior

### Cons
- Memory intensive: O(n) where n = requests in window
- Slower than O(1) algorithms for high traffic

### Memory Example
```
1000 req/sec limit
Each timestamp: 8 bytes
Memory per user: 8 KB
For 1M users: 8 GB RAM
```

### Best For
- Systems requiring precise rate limiting
- Analytics and monitoring systems
- Low to medium traffic APIs

---

## 4. Sliding Window Counter (Production Favorite!)

### Concept
Hybrid approach: Uses two counters (previous + current window) and estimates the sliding window count using a weighted average.

### Formula
```
estimatedCount = (previousCount × previousWeight) + currentCount
previousWeight = 1.0 - (timeIntoCurrentWindow / windowSize)
```

### Pros
- O(1) time and space complexity
- Approximates sliding window accurately (typically <5% error)
- No boundary attack vulnerability
- Production-proven at scale

### Cons
- Slightly less accurate than sliding log (but acceptable)
- More complex implementation than fixed window

### Example Calculation
```
Current time: 10:00:45 (45 seconds into window)
Previous window count: 80
Current window count: 30
Window size: 60 seconds

windowProgress = 45 / 60 = 0.75 (75% through window)
previousWeight = 1.0 - 0.75 = 0.25 (25% from previous)

estimatedCount = (80 × 0.25) + 30 = 20 + 30 = 50
```

### Best For
- **Production APIs at scale**
- Distributed systems
- High-throughput services
- General-purpose rate limiting

---

## Recommendation

**For this project, we use Sliding Window Counter** because:
1. Best balance of accuracy and efficiency
2. Industry-proven (Cloudflare, Redis, Kong)
3. Works well with distributed Redis storage
4. Handles high throughput
5. Resistant to boundary attacks

---

## Interview Talking Points

When asked about rate limiting in interviews, mention:

1. **Algorithm knowledge**: "I've implemented 4 different rate limiting algorithms"
2. **Tradeoffs**: "Fixed window is fast but has boundary vulnerabilities, while sliding log is accurate but memory-intensive"
3. **Production choice**: "I chose sliding window counter for production because it's used by Cloudflare and provides O(1) complexity with good accuracy"
4. **Distributed systems**: "I integrated these with Redis using Lua scripts for atomic operations"
5. **Real metrics**: "Token bucket handles bursts well, but sliding counter is more predictable for distributed systems"

---

## References

- [Cloudflare: How We Built Rate Limiting](https://blog.cloudflare.com/counting-things-a-lot-of-different-things/)
- [Redis: Rate Limiting Pattern](https://redis.io/glossary/rate-limiting/)
- [Stripe: Scaling API Rate Limiting](https://stripe.com/blog/rate-limiters)