package com.ratelimiter.core.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Component
public class RedisLuaSlidingWindowCounterRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "ratelimit:slidingcounter:";
    private final DefaultRedisScript<List> allowScript;

    // Cache for storing result from last request (for quick access)
    private static class CachedResult {
        boolean allowed;
        int previousCount;
        int currentCount;
        double estimatedCount;
        long windowStart;
    }

    private final ThreadLocal<CachedResult> lastResult = ThreadLocal.withInitial(CachedResult::new);

    public RedisLuaSlidingWindowCounterRateLimiter() {
        // Load Lua script
        this.allowScript = new DefaultRedisScript<>();
        this.allowScript.setResultType(List.class);

        try {
            ClassPathResource resource = new ClassPathResource("lua/sliding_window_counter_allow.lua");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            this.allowScript.setScriptText(script);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }

    /**
     * Check if request is allowed (OPTIMIZED - single Redis call)
     */
    public boolean allowRequest(String userId, int maxRequests, long windowSizeMillis, int ttl) {
        String previousKey = KEY_PREFIX + userId + ":previous";
        String currentKey = KEY_PREFIX + userId + ":current";
        String windowStartKey = KEY_PREFIX + userId + ":windowStart";

        long currentTime = Instant.now().toEpochMilli();

        // Execute Lua script - returns [allowed, previousCount, currentCount, estimatedCount, windowStart]
        List<Object> result = redisTemplate.execute(
                allowScript,
                List.of(previousKey, currentKey, windowStartKey),
                maxRequests, windowSizeMillis, currentTime, ttl
        );

        if (result != null && result.size() >= 5) {
            // Cache the result for subsequent calls
            CachedResult cached = lastResult.get();
            cached.allowed = ((Number) result.get(0)).intValue() == 1;
            cached.previousCount = ((Number) result.get(1)).intValue();
            cached.currentCount = ((Number) result.get(2)).intValue();
            cached.estimatedCount = ((Number) result.get(3)).doubleValue();
            cached.windowStart = ((Number) result.get(4)).longValue();

            return cached.allowed;
        }

        return false;
    }

    /**
     * Get estimated count (uses cached data from last allowRequest call)
     */
    public double getEstimatedCount(String userId, long windowSizeMillis) {
        CachedResult cached = lastResult.get();
        if (cached.estimatedCount > 0) {
            return cached.estimatedCount;
        }

        // Fallback: make a separate call if no cached data
        return getEstimatedCountDirect(userId, windowSizeMillis);
    }

    /**
     * Direct call to get estimated count (slower, used as fallback)
     */
    private double getEstimatedCountDirect(String userId, long windowSizeMillis) {
        String previousKey = KEY_PREFIX + userId + ":previous";
        String currentKey = KEY_PREFIX + userId + ":current";
        String windowStartKey = KEY_PREFIX + userId + ":windowStart";

        long currentTime = Instant.now().toEpochMilli();

        Object prevObj = redisTemplate.opsForValue().get(previousKey);
        Object currObj = redisTemplate.opsForValue().get(currentKey);
        Object windowStartObj = redisTemplate.opsForValue().get(windowStartKey);

        if (currObj == null) {
            return 0.0;
        }

        int previousCount = prevObj != null ? Integer.parseInt(prevObj.toString()) : 0;
        int currentCount = Integer.parseInt(currObj.toString());

        if (windowStartObj != null) {
            long windowStart = Long.parseLong(windowStartObj.toString());
            double timeIntoWindow = currentTime - windowStart;
            double windowProgress = timeIntoWindow / windowSizeMillis;
            double previousWeight = Math.max(0, 1.0 - windowProgress);

            return (previousCount * previousWeight) + currentCount;
        }

        return currentCount;
    }

    /**
     * Get debug information (uses cached data when possible)
     */
    public String getDebugInfo(String userId, long windowSizeMillis) {
        CachedResult cached = lastResult.get();

        if (cached.estimatedCount > 0) {
            long currentTime = Instant.now().toEpochMilli();
            double windowProgress = ((currentTime - cached.windowStart) / (double) windowSizeMillis) * 100;

            return String.format(
                    "Previous: %d | Current: %d | Estimated: %.2f | Progress: %.1f%%",
                    cached.previousCount, cached.currentCount, cached.estimatedCount, windowProgress
            );
        }

        // Fallback: get fresh data
        return getDebugInfoDirect(userId, windowSizeMillis);
    }

    /**
     * Direct call to get debug info (slower, used as fallback)
     */
    private String getDebugInfoDirect(String userId, long windowSizeMillis) {
        String previousKey = KEY_PREFIX + userId + ":previous";
        String currentKey = KEY_PREFIX + userId + ":current";
        String windowStartKey = KEY_PREFIX + userId + ":windowStart";

        long currentTime = Instant.now().toEpochMilli();

        Object prevObj = redisTemplate.opsForValue().get(previousKey);
        Object currObj = redisTemplate.opsForValue().get(currentKey);
        Object windowStartObj = redisTemplate.opsForValue().get(windowStartKey);

        int previous = prevObj != null ? Integer.parseInt(prevObj.toString()) : 0;
        int current = currObj != null ? Integer.parseInt(currObj.toString()) : 0;
        double estimated = getEstimatedCountDirect(userId, windowSizeMillis);

        double windowProgress = 0;
        if (windowStartObj != null) {
            long windowStart = Long.parseLong(windowStartObj.toString());
            windowProgress = ((currentTime - windowStart) / (double) windowSizeMillis) * 100;
        }

        return String.format(
                "Previous: %d | Current: %d | Estimated: %.2f | Progress: %.1f%%",
                previous, current, estimated, windowProgress
        );
    }
}