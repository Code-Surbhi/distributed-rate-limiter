package com.ratelimiter.core.service;

import com.ratelimiter.core.model.RateLimitResponse;
import com.ratelimiter.core.model.RateLimitRule;
import com.ratelimiter.core.redis.RedisLuaTokenBucketRateLimiter;
import com.ratelimiter.core.redis.RedisLuaSlidingWindowCounterRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RateLimiterService {

    @Autowired
    private RedisLuaTokenBucketRateLimiter tokenBucketLimiter;

    @Autowired
    private RedisLuaSlidingWindowCounterRateLimiter slidingWindowLimiter;

    // In-memory configuration (later: load from database)
    private final Map<String, RateLimitRule> rules = new HashMap<>();

    public RateLimiterService() {
        initializeDefaultRules();
    }

    /**
     * Initialize default rate limit rules
     * Later: Load from PostgreSQL
     */
    private void initializeDefaultRules() {
        // Free tier: 100 requests per hour
        rules.put("free-tier", RateLimitRule.builder()
                .apiKey("free-tier")
                .algorithm("SLIDING_WINDOW")
                .maxRequests(100)
                .windowSizeMillis(3_600_000L) // Add L for Long
                .ttl(7200)
                .build());

        // Pro tier: 1000 requests per hour
        rules.put("pro-tier", RateLimitRule.builder()
                .apiKey("pro-tier")
                .algorithm("SLIDING_WINDOW")
                .maxRequests(1000)
                .windowSizeMillis(3_600_000L) // Add L for Long
                .ttl(7200)
                .build());

        // Enterprise tier: 10000 requests per hour with token bucket
        rules.put("enterprise-tier", RateLimitRule.builder()
                .apiKey("enterprise-tier")
                .algorithm("TOKEN_BUCKET")
                .capacity(10000)
                .refillRate(3) // 3 tokens per second ≈ 10,800/hour
                .ttl(7200)
                .build());
    }

    /**
     * Check if request is allowed for given user and API key
     *
     * @param userId User making the request
     * @param apiKey API key being used
     * @return RateLimitResponse with decision and metadata
     */
    public RateLimitResponse checkRateLimit(String userId, String apiKey) {
        // Get rule for this API key
        RateLimitRule rule = rules.get(apiKey);

        if (rule == null) {
            log.warn("No rate limit rule found for apiKey: {}, using default", apiKey);
            rule = rules.get("free-tier"); // Default to free tier
        }

        // Build user identifier (combine userId + apiKey for isolation)
        String identifier = userId + ":" + apiKey;

        // Apply rate limiting based on algorithm
        // Apply rate limiting based on algorithm
        boolean allowed;
        int remaining = 0;
        double estimated = 0;

        if ("TOKEN_BUCKET".equals(rule.getAlgorithm())) {
            allowed = tokenBucketLimiter.allowRequest(
                    identifier,
                    rule.getCapacity(),
                    rule.getRefillRate(),
                    rule.getTtl()
            );
            remaining = tokenBucketLimiter.getRemainingTokens(
                    identifier,
                    rule.getCapacity(),
                    rule.getRefillRate()
            );
        } else {
            // Default to SLIDING_WINDOW
            allowed = slidingWindowLimiter.allowRequest(
                    identifier,
                    rule.getMaxRequests(),
                    rule.getWindowSizeMillis(),
                    rule.getTtl()
            );
            estimated = slidingWindowLimiter.getEstimatedCount(
                    identifier,
                    rule.getWindowSizeMillis()
            );
            remaining = (int) Math.max(0, rule.getMaxRequests() - Math.ceil(estimated));
        }

// Build response - FIX THIS LINE
        return RateLimitResponse.builder()
                .allowed(allowed)
                .limit(rule.getMaxRequests() != null ? rule.getMaxRequests() : rule.getCapacity())
                .remaining(remaining)
                .resetTime(calculateResetTime(rule))
                .algorithm(rule.getAlgorithm())
                .userId(userId)
                .apiKey(apiKey)
                .estimatedCount(estimated)
                .build();
    }

    /**
     * Calculate when the rate limit will reset
     */
    private long calculateResetTime(RateLimitRule rule) {
        long now = Instant.now().toEpochMilli();

        if ("SLIDING_WINDOW".equals(rule.getAlgorithm())) {
            // For sliding window, calculate next window boundary
            long currentWindowStart = (now / rule.getWindowSizeMillis()) * rule.getWindowSizeMillis();
            return currentWindowStart + rule.getWindowSizeMillis();
        } else {
            // For token bucket, tokens continuously refill
            // Return time when bucket will be full again
            // Simplified: return current time + 1 minute
            return now + 60_000;
        }
    }

    /**
     * Add or update a rate limit rule
     */
    public void addRule(RateLimitRule rule) {
        rules.put(rule.getApiKey(), rule);
        log.info("Added rate limit rule for apiKey: {}", rule.getApiKey());
    }

    /**
     * Get rule for API key
     */
    public RateLimitRule getRule(String apiKey) {
        return rules.get(apiKey);
    }
}