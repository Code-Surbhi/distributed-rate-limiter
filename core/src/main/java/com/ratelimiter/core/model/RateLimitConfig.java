package com.ratelimiter.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfig {
    private int capacity;        // Maximum tokens
    private int refillRate;      // Tokens added per second
    private String algorithm;    // Which algorithm to use
}