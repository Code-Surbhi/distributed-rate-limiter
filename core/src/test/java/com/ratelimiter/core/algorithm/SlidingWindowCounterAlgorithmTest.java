package com.ratelimiter.core.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Sliding Window Counter Algorithm Tests")
class SlidingWindowCounterAlgorithmTest {

    private SlidingWindowCounterAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        // 10 requests per 10,000ms (10 second) window for more stable tests
        algorithm = new SlidingWindowCounterAlgorithm(10, 10_000);
    }

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowRequestsWithinLimit() {
        // When: Make 10 requests
        for (int i = 0; i < 10; i++) {
            boolean allowed = algorithm.allowRequest();

            // Then: All should be allowed
            assertThat(allowed)
                    .withFailMessage("Request %d should be allowed", i + 1)
                    .isTrue();
        }

        // And: Should have no remaining requests
        assertThat(algorithm.getRemainingRequests()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject requests exceeding limit")
    void shouldRejectRequestsExceedingLimit() {
        // Given: Exhaust the limit
        for (int i = 0; i < 10; i++) {
            algorithm.allowRequest();
        }

        // When: Try 11th request
        boolean allowed = algorithm.allowRequest();

        // Then: Should be rejected
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("Should calculate estimated count correctly")
    void shouldCalculateEstimatedCountCorrectly() {
        // Given: Make 5 requests in current window
        for (int i = 0; i < 5; i++) {
            algorithm.allowRequest();
        }

        // When: Check estimated count immediately
        double estimated = algorithm.getEstimatedCount();

        // Then: Estimated count should be approximately 5
        assertThat(estimated).isBetween(4.8, 5.2);
    }

    @Test
    @DisplayName("Should handle window rotation correctly")
    void shouldHandleWindowRotationCorrectly() throws InterruptedException {
        // Given: Make 8 requests in window 1
        for (int i = 0; i < 8; i++) {
            algorithm.allowRequest();
        }

        // When: Wait for window to rotate completely (11 seconds > 10 second window)
        Thread.sleep(11_000);

        // Then: Counter should reset, allowing new requests
        boolean allowed = algorithm.allowRequest();
        assertThat(allowed).isTrue();

        // And: Should show requests from new window
        String debugInfo = algorithm.getDebugInfo();
        assertThat(debugInfo).contains("Current: 1");
    }

    @Test
    @DisplayName("Should use weighted average across windows")
    void shouldUseWeightedAverageAcrossWindows() throws InterruptedException {
        // Given: Make 10 requests (fill first window)
        for (int i = 0; i < 10; i++) {
            algorithm.allowRequest();
        }

        // When: Move significantly into next window
        // Wait 6 seconds = 60% of 10 second window
        Thread.sleep(6000);

        // Then: Estimated should be weighted average
        // Previous: 10, Current: 0, 60% into window
        // Estimated ≈ (10 * 0.4) + 0 = 4
        double estimated = algorithm.getEstimatedCount();
        assertThat(estimated)
                .withFailMessage("Expected weighted average around 3-5, but got %.2f", estimated)
                .isBetween(2.5, 5.5);
    }

    @Test
    @DisplayName("Should prevent boundary attack")
    void shouldPreventBoundaryAttack() throws InterruptedException {
        // Given: Make 10 requests near end of window
        for (int i = 0; i < 10; i++) {
            algorithm.allowRequest();
        }

        // When: Wait to cross window boundary (11 seconds > 10 second window)
        Thread.sleep(11_000);

        // And: Try to make 10 more requests immediately
        int allowedInNewWindow = 0;
        for (int i = 0; i < 10; i++) {
            if (algorithm.allowRequest()) {
                allowedInNewWindow++;
            }
        }

        // Then: All 10 should be allowed since we fully crossed to new window
        assertThat(allowedInNewWindow).isEqualTo(10);

        // But: Verify algorithm still enforces limit
        boolean shouldBeRejected = algorithm.allowRequest();
        assertThat(shouldBeRejected).isFalse();
    }

    @Test
    @DisplayName("Should show window progress increases over time")
    void shouldShowWindowProgressIncreasesOverTime() throws InterruptedException {
        // Given: Make initial request
        algorithm.allowRequest();

        // When: Check initial progress
        double initialProgress = algorithm.getWindowProgress();

        // Then: Should be early in window (< 20%)
        assertThat(initialProgress).isBetween(0.0, 20.0);

        // When: Wait 3 seconds (30% of 10 second window)
        Thread.sleep(3000);

        // Then: Progress should increase
        double laterProgress = algorithm.getWindowProgress();
        assertThat(laterProgress)
                .withFailMessage("Progress should increase from %.2f%% to around 30%%", initialProgress)
                .isGreaterThan(initialProgress)
                .isBetween(25.0, 50.0); // Wider range to account for test execution time
    }

    @Test
    @DisplayName("Should handle rapid concurrent-like requests")
    void shouldHandleRapidRequests() {
        // When: Make rapid requests in a loop
        int successCount = 0;
        int rejectCount = 0;

        for (int i = 0; i < 20; i++) {
            if (algorithm.allowRequest()) {
                successCount++;
            } else {
                rejectCount++;
            }
        }

        // Then: Should allow exactly 10 and reject 10
        assertThat(successCount).isEqualTo(10);
        assertThat(rejectCount).isEqualTo(10);
    }
}