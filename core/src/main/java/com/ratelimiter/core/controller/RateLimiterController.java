package com.ratelimiter.core.controller;

import com.ratelimiter.core.model.RateLimitResponse;
import com.ratelimiter.core.model.RateLimitRule;
import com.ratelimiter.core.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ratelimit")
public class RateLimiterController {

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * Check if request is allowed
     *
     * Usage: GET /api/v1/ratelimit/check?userId=user123&apiKey=free-tier
     */
    @GetMapping("/check")
    public ResponseEntity<RateLimitResponse> checkRateLimit(
            @RequestParam String userId,
            @RequestParam String apiKey) {

        RateLimitResponse response = rateLimiterService.checkRateLimit(userId, apiKey);

        if (response.isAllowed()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
    }

    /**
     * Get rate limit rule for API key
     */
    @GetMapping("/rules/{apiKey}")
    public ResponseEntity<RateLimitRule> getRule(@PathVariable String apiKey) {
        RateLimitRule rule = rateLimiterService.getRule(apiKey);

        if (rule != null) {
            return ResponseEntity.ok(rule);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Add or update rate limit rule
     */
    @PostMapping("/rules")
    public ResponseEntity<String> addRule(@RequestBody RateLimitRule rule) {
        rateLimiterService.addRule(rule);
        return ResponseEntity.ok("Rule added successfully");
    }
}