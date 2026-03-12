package com.ratelimiter.core.algorithm;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketAlgorithm {

    private final int capacity;           // Max tokens
    private final int refillRate;         // Tokens per second
    private final AtomicInteger tokens;   // Current tokens (thread-safe)
    private final AtomicLong lastRefillTime; // Last refill timestamp (thread-safe)

    public TokenBucketAlgorithm(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.tokens = new AtomicInteger(capacity); // Start with full bucket
        this.lastRefillTime = new AtomicLong(Instant.now().toEpochMilli());
    }

    /**
     * Try to consume a token
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest() {
        refill();

        // Try to take a token
        int currentTokens = tokens.get();
        if (currentTokens > 0) {
            // Atomic decrement - thread-safe
            if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return true;  // Request allowed ✅
            }
        }

        return false;  // Rate limited ❌
    }

    /**
     * Refill tokens based on time passed
     */
    private void refill() {
        long now = Instant.now().toEpochMilli();
        long lastRefill = lastRefillTime.get();

        // Calculate time passed in seconds
        long timePassed = (now - lastRefill) / 1000;

        if (timePassed > 0) {
            // Calculate tokens to add
            int tokensToAdd = (int) (timePassed * refillRate);

            if (tokensToAdd > 0) {
                int currentTokens = tokens.get();
                int newTokens = Math.min(currentTokens + tokensToAdd, capacity);

                // Update tokens and timestamp atomically
                tokens.set(newTokens);
                lastRefillTime.set(now);
            }
        }
    }

    /**
     * Get remaining tokens (for debugging/monitoring)
     */
    public int getRemainingTokens() {
        refill();
        return tokens.get();
    }
}