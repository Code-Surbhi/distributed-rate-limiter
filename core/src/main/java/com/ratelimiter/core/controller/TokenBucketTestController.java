package com.ratelimiter.core.controller;

import com.ratelimiter.core.algorithm.TokenBucketAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TokenBucketTestController {

    // Create a bucket: 10 tokens capacity, refill 2 tokens/second
    private final TokenBucketAlgorithm bucket = new TokenBucketAlgorithm(10, 2);

    @GetMapping("/token-bucket")
    public String testTokenBucket() {
        if (bucket.allowRequest()) {
            return "✅ Request allowed! Remaining tokens: " + bucket.getRemainingTokens();
        } else {
            return "❌ Rate limited! Try again later. Remaining tokens: " + bucket.getRemainingTokens();
        }
    }
}