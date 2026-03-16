package com.ratelimiter.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    private String apiKey;              // API key this rule applies to
    private String algorithm;           // "TOKEN_BUCKET" or "SLIDING_WINDOW"
    private Integer maxRequests;        // Maximum requests allowed (use Integer, not int)
    private Long windowSizeMillis;      // Time window in milliseconds (use Long, not long)
    private Integer ttl;                // TTL for Redis keys in seconds

    // Token Bucket specific
    private Integer capacity;           // Bucket capacity (null if not Token Bucket)
    private Integer refillRate;         // Tokens per second (null if not Token Bucket)
}