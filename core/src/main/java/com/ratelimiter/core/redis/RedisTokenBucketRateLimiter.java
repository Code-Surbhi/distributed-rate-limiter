package com.ratelimiter.core.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class RedisTokenBucketRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "ratelimit:tokenbucket:";

    /**
     * Check if request is allowed for a user
     *
     * @param userId Unique identifier for the user
     * @param capacity Maximum tokens in bucket
     * @param refillRate Tokens added per second
     * @param ttl Time to live for keys in seconds (cleanup)
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest(String userId, int capacity, int refillRate, int ttl) {
        String tokensKey = KEY_PREFIX + userId + ":tokens";
        String lastRefillKey = KEY_PREFIX + userId + ":lastRefill";

        long now = Instant.now().toEpochMilli();

        // Get current state from Redis (safely handle String → Integer conversion)
        Integer currentTokens = getIntegerValue(tokensKey);
        Long lastRefillTime = getLongValue(lastRefillKey);

        // First request - initialize bucket
        if (currentTokens == null || lastRefillTime == null) {
            // Start with full bucket minus one for this request
            redisTemplate.opsForValue().set(tokensKey, String.valueOf(capacity - 1), ttl, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(lastRefillKey, String.valueOf(now), ttl, TimeUnit.SECONDS);
            return true;  // Allow first request
        }

        // Calculate tokens to add based on time passed
        long timePassed = (now - lastRefillTime) / 1000; // Convert to seconds
        int tokensToAdd = (int) (timePassed * refillRate);

        // Refill tokens
        int newTokens = Math.min(currentTokens + tokensToAdd, capacity);

        // Check if we have tokens available
        if (newTokens > 0) {
            // Consume one token
            redisTemplate.opsForValue().set(tokensKey, String.valueOf(newTokens - 1), ttl, TimeUnit.SECONDS);

            // Only update lastRefill if we actually added tokens
            if (tokensToAdd > 0) {
                redisTemplate.opsForValue().set(lastRefillKey, String.valueOf(now), ttl, TimeUnit.SECONDS);
            }

            return true;  // Request allowed ✅
        }

        return false;  // Rate limited ❌
    }

    /**
     * Get remaining tokens for a user
     */
    public int getRemainingTokens(String userId, int capacity, int refillRate) {
        String tokensKey = KEY_PREFIX + userId + ":tokens";
        String lastRefillKey = KEY_PREFIX + userId + ":lastRefill";

        long now = Instant.now().toEpochMilli();

        Integer currentTokens = getIntegerValue(tokensKey);
        Long lastRefillTime = getLongValue(lastRefillKey);

        if (currentTokens == null) {
            return capacity;  // Bucket is full (no requests yet)
        }

        // Calculate refill
        long timePassed = (now - (lastRefillTime != null ? lastRefillTime : now)) / 1000;
        int tokensToAdd = (int) (timePassed * refillRate);

        return Math.min(currentTokens + tokensToAdd, capacity);
    }

    /**
     * Helper: Safely get Integer value from Redis
     */
    private Integer getIntegerValue(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper: Safely get Long value from Redis
     */
    private Long getLongValue(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}