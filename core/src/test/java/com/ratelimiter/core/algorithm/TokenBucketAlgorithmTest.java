package com.ratelimiter.core.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Token Bucket Algorithm Tests")
class TokenBucketAlgorithmTest {

    private TokenBucketAlgorithm tokenBucket;

    @BeforeEach
    void setUp() {
        // Create bucket: 10 tokens capacity, 2 tokens/second refill
        tokenBucket = new TokenBucketAlgorithm(10, 2);
    }

    @Test
    @DisplayName("Should allow requests when tokens available")
    void shouldAllowRequestsWhenTokensAvailable() {
        // When: Make 5 requests
        for (int i = 0; i < 5; i++) {
            boolean allowed = tokenBucket.allowRequest();

            // Then: All should be allowed
            assertThat(allowed).isTrue();
        }

        // And: Should have 5 tokens remaining
        assertThat(tokenBucket.getRemainingTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reject requests when bucket empty")
    void shouldRejectRequestsWhenBucketEmpty() {
        // Given: Exhaust all tokens
        for (int i = 0; i < 10; i++) {
            tokenBucket.allowRequest();
        }

        // When: Try one more request
        boolean allowed = tokenBucket.allowRequest();

        // Then: Should be rejected
        assertThat(allowed).isFalse();
        assertThat(tokenBucket.getRemainingTokens()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should refill tokens over time")
    void shouldRefillTokensOverTime() throws InterruptedException {
        // Given: Exhaust all tokens
        for (int i = 0; i < 10; i++) {
            tokenBucket.allowRequest();
        }
        assertThat(tokenBucket.getRemainingTokens()).isEqualTo(0);

        // When: Wait 2 seconds (should refill 4 tokens at 2/sec)
        Thread.sleep(2000);

        // Then: Should have refilled tokens
        int remaining = tokenBucket.getRemainingTokens();
        assertThat(remaining).isGreaterThanOrEqualTo(3); // At least 3 (accounting for timing)
        assertThat(remaining).isLessThanOrEqualTo(5);    // At most 5
    }

    @Test
    @DisplayName("Should not exceed capacity when refilling")
    void shouldNotExceedCapacityWhenRefilling() throws InterruptedException {
        // Given: Use 5 tokens
        for (int i = 0; i < 5; i++) {
            tokenBucket.allowRequest();
        }

        // When: Wait long enough to refill way more than capacity
        Thread.sleep(10_000); // 10 seconds = 20 tokens worth

        // Then: Should cap at 10 (capacity)
        assertThat(tokenBucket.getRemainingTokens()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should handle burst traffic correctly")
    void shouldHandleBurstTrafficCorrectly() {
        // Given: Full bucket (10 tokens)

        // When: Burst of 10 requests
        int allowedCount = 0;
        for (int i = 0; i < 15; i++) {
            if (tokenBucket.allowRequest()) {
                allowedCount++;
            }
        }

        // Then: Should allow exactly 10 (the capacity)
        assertThat(allowedCount).isEqualTo(10);
        assertThat(tokenBucket.getRemainingTokens()).isEqualTo(0);
    }
}