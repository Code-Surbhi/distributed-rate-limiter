package com.ratelimiter.core.controller;

import com.ratelimiter.core.redis.RedisTokenBucketRateLimiter;
import com.ratelimiter.core.redis.RedisLuaTokenBucketRateLimiter;
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

    @Autowired
    private RedisLuaTokenBucketRateLimiter luaTokenBucketLimiter;

    @GetMapping("/token-bucket")
    public String testTokenBucket(
            @RequestParam(defaultValue = "user123") String userId) {

        int capacity = 10;
        int refillRate = 2;
        int ttl = 300;

        boolean allowed = tokenBucketLimiter.allowRequest(userId, capacity, refillRate, ttl);
        int remaining = tokenBucketLimiter.getRemainingTokens(userId, capacity, refillRate);

        if (allowed) {
            return String.format(
                    "✅ Request allowed for %s | Remaining: %d/%d",
                    userId, remaining, capacity
            );
        } else {
            return String.format(
                    "❌ Rate limited for %s | Remaining: %d/%d",
                    userId, remaining, capacity
            );
        }
    }

    @GetMapping("/token-bucket-lua")
    public String testTokenBucketLua(
            @RequestParam(defaultValue = "user123") String userId) {

        int capacity = 10;
        int refillRate = 2;
        int ttl = 300;

        boolean allowed = luaTokenBucketLimiter.allowRequest(userId, capacity, refillRate, ttl);
        int remaining = luaTokenBucketLimiter.getRemainingTokens(userId, capacity, refillRate);

        if (allowed) {
            return String.format(
                    "✅ [LUA] Request allowed for %s | Remaining: %d/%d | No race conditions!",
                    userId, remaining, capacity
            );
        } else {
            return String.format(
                    "❌ [LUA] Rate limited for %s | Remaining: %d/%d",
                    userId, remaining, capacity
            );
        }
    }
}