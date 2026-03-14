package com.ratelimiter.core.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/redis")
public class RedisTestController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/test")
    public String testRedis() {
        try {
            // Set a test key with 60 second expiration
            redisTemplate.opsForValue().set("test:key", "Hello Redis!", 60, TimeUnit.SECONDS);

            // Get the value back
            String value = (String) redisTemplate.opsForValue().get("test:key");

            return "✅ Redis connected! Value: " + value;
        } catch (Exception e) {
            return "❌ Redis connection failed: " + e.getMessage();
        }
    }

    @GetMapping("/increment")
    public String testIncrement() {
        String key = "test:counter";

        // Increment counter
        Long count = redisTemplate.opsForValue().increment(key);

        // Set expiration on first increment
        if (count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        return "✅ Counter incremented! Current value: " + count;
    }
}