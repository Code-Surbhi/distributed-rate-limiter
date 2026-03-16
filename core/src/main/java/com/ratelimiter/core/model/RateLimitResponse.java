package com.ratelimiter.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResponse {

    private boolean allowed;            // Is request allowed?
    private Integer limit;              // Total limit (Integer, not int)
    private Integer remaining;          // Remaining requests (Integer, not int)
    private Long resetTime;             // When limit resets (Long, not long)
    private String algorithm;           // Algorithm used

    // Additional info for debugging/monitoring
    private String userId;
    private String apiKey;
    private Double estimatedCount;      // For sliding window (Double, not double)
}