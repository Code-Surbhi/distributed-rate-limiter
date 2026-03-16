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
    private final DefaultRedisScript<Long> allowScript;

    public RedisLuaSlidingWindowCounterRateLimiter() {
        // Load Lua script
        this.allowScript = new DefaultRedisScript<>();
        this.allowScript.setResultType(Long.class);

        try {
            ClassPathResource resource = new ClassPathResource("lua/sliding_window_counter_allow.lua");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            this.allowScript.setScriptText(script);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }

    /**
     * Check if request is allowed
     *
     * @param userId User identifier
     * @param maxRequests Maximum requests per window
     * @param windowSizeMillis Window size in milliseconds
     * @param ttl TTL for Redis keys in seconds
     * @return true if allowed, false if rate limited
     */
    public boolean allowRequest(String userId, int maxRequests, long windowSizeMillis, int ttl) {
        String previousKey = KEY_PREFIX + userId + ":previous";
        String currentKey = KEY_PREFIX + userId + ":current";
        String windowStartKey = KEY_PREFIX + userId + ":windowStart";

        long currentTime = Instant.now().toEpochMilli();

        // Execute Lua script atomically
        Long result = redisTemplate.execute(
                allowScript,
                List.of(previousKey, currentKey, windowStartKey),
                maxRequests, windowSizeMillis, currentTime, ttl
        );

        return result != null && result == 1;
    }

    /**
     * Get estimated request count in sliding window
     */
    public double getEstimatedCount(String userId, long windowSizeMillis) {
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
     * Get debug information
     */
    public String getDebugInfo(String userId, long windowSizeMillis) {
        String previousKey = KEY_PREFIX + userId + ":previous";
        String currentKey = KEY_PREFIX + userId + ":current";
        String windowStartKey = KEY_PREFIX + userId + ":windowStart";

        long currentTime = Instant.now().toEpochMilli();

        Object prevObj = redisTemplate.opsForValue().get(previousKey);
        Object currObj = redisTemplate.opsForValue().get(currentKey);
        Object windowStartObj = redisTemplate.opsForValue().get(windowStartKey);

        int previous = prevObj != null ? Integer.parseInt(prevObj.toString()) : 0;
        int current = currObj != null ? Integer.parseInt(currObj.toString()) : 0;
        double estimated = getEstimatedCount(userId, windowSizeMillis);

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