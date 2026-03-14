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
public class RedisLuaTokenBucketRateLimiter {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "ratelimit:tokenbucket:";
    private final DefaultRedisScript<Long> allowScript;

    public RedisLuaTokenBucketRateLimiter() {
        // Load Lua script from classpath
        this.allowScript = new DefaultRedisScript<>();
        this.allowScript.setResultType(Long.class);

        try {
            ClassPathResource resource = new ClassPathResource("lua/token_bucket_allow.lua");
            String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            this.allowScript.setScriptText(script);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Lua script", e);
        }
    }

    /**
     * Check if request is allowed (using atomic Lua script)
     *
     * @param userId     Unique identifier for the user
     * @param capacity   Maximum tokens in bucket
     * @param refillRate Tokens added per second
     * @param ttl        Time to live for keys in seconds
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest(String userId, int capacity, int refillRate, int ttl) {
        String tokensKey = KEY_PREFIX + userId + ":tokens";
        String lastRefillKey = KEY_PREFIX + userId + ":lastRefill";

        long currentTime = Instant.now().toEpochMilli();

        // Execute Lua script atomically
        Long result = redisTemplate.execute(
                allowScript,
                List.of(tokensKey, lastRefillKey),  // KEYS
                capacity, refillRate, currentTime, ttl  // ARGV
        );

        return result != null && result == 1;
    }

    /**
     * Get remaining tokens for a user
     */
    public int getRemainingTokens(String userId, int capacity, int refillRate) {
        String tokensKey = KEY_PREFIX + userId + ":tokens";
        String lastRefillKey = KEY_PREFIX + userId + ":lastRefill";

        long now = Instant.now().toEpochMilli();

        Object tokensObj = redisTemplate.opsForValue().get(tokensKey);
        Object lastRefillObj = redisTemplate.opsForValue().get(lastRefillKey);

        if (tokensObj == null) {
            return capacity;
        }

        int currentTokens = Integer.parseInt(tokensObj.toString());

        if (lastRefillObj != null) {
            long lastRefillTime = Long.parseLong(lastRefillObj.toString());
            long timePassed = (now - lastRefillTime) / 1000;
            int tokensToAdd = (int) (timePassed * refillRate);
            return Math.min(currentTokens + tokensToAdd, capacity);
        }

        return currentTokens;
    }
}