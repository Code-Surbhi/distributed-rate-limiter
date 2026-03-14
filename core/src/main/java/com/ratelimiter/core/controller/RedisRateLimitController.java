package com.ratelimiter.core.controller;

import com.ratelimiter.core.redis.RedisTokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ratelimit")
public class RedisRateLimitController {

    @Autowired
    private RedisTokenBucketRateLimiter tokenBucketLimiter;

    @GetMapping("/token-bucket")
    public String testTokenBucket(
            @RequestParam(defaultValue = "user123") String userId) {

        int capacity = 10;
        int refillRate = 2;  // 2 tokens per second
        int ttl = 300;  // 5 minutes

        boolean allowed = tokenBucketLimiter.allowRequest(userId, capacity, refillRate, ttl);
        int remaining = tokenBucketLimiter.getRemainingTokens(userId, capacity, refillRate);

        if (allowed) {
            return String.format(
                    "✅ Request allowed for %s | Remaining tokens: %d/%d",
                    userId, remaining, capacity
            );
        } else {
            return String.format(
                    "❌ Rate limited for %s | Remaining tokens: %d/%d",
                    userId, remaining, capacity
            );
        }
    }
}