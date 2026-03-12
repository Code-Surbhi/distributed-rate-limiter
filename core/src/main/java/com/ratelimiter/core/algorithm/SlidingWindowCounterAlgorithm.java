package com.ratelimiter.core.algorithm;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SlidingWindowCounterAlgorithm {

    private final int maxRequests;                  // Request limit per window
    private final long windowSizeMillis;            // Window size in milliseconds
    private final AtomicInteger previousWindowCount; // Count from previous window
    private final AtomicInteger currentWindowCount;  // Count in current window
    private final AtomicLong currentWindowStart;     // Start time of current window

    public SlidingWindowCounterAlgorithm(int maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.previousWindowCount = new AtomicInteger(0);
        this.currentWindowCount = new AtomicInteger(0);
        this.currentWindowStart = new AtomicLong(getCurrentWindowStart());
    }

    /**
     * Try to allow a request
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest() {
        long now = Instant.now().toEpochMilli();
        updateWindowIfNeeded(now);

        // Calculate estimated count using weighted formula
        double estimatedCount = getEstimatedCount(now);

        // Check if we're within the limit
        if (estimatedCount < maxRequests) {
            currentWindowCount.incrementAndGet();
            return true;  // Request allowed ✅
        }

        return false;  // Rate limited ❌
    }

    /**
     * Calculate estimated request count in sliding window
     * Uses weighted average of previous and current windows
     */
    private double getEstimatedCount(long currentTime) {
        long windowStart = currentWindowStart.get();

        // Calculate how far we are into the current window (0.0 to 1.0)
        double timeIntoCurrentWindow = currentTime - windowStart;
        double windowProgress = timeIntoCurrentWindow / windowSizeMillis;

        // Weight for previous window decreases as we move through current window
        double previousWindowWeight = Math.max(0, 1.0 - windowProgress);

        // Weighted average
        int previous = previousWindowCount.get();
        int current = currentWindowCount.get();

        return (previous * previousWindowWeight) + current;
    }

    /**
     * Check if we've moved to a new window and update counters
     */
    private void updateWindowIfNeeded(long currentTime) {
        long expectedWindowStart = getCurrentWindowStart();
        long storedWindowStart = currentWindowStart.get();

        // If we've moved to a new window
        if (expectedWindowStart > storedWindowStart) {
            // Move current count to previous
            previousWindowCount.set(currentWindowCount.get());
            // Reset current count
            currentWindowCount.set(0);
            // Update window start time
            currentWindowStart.set(expectedWindowStart);
        }
    }

    /**
     * Calculate the start time of the current window
     */
    private long getCurrentWindowStart() {
        long now = Instant.now().toEpochMilli();
        return (now / windowSizeMillis) * windowSizeMillis;
    }

    /**
     * Get remaining requests in sliding window
     */
    public int getRemainingRequests() {
        long now = Instant.now().toEpochMilli();
        updateWindowIfNeeded(now);

        double estimatedCount = getEstimatedCount(now);
        return (int) Math.max(0, maxRequests - Math.ceil(estimatedCount));
    }

    /**
     * Get current estimated count (for monitoring)
     */
    public double getEstimatedCount() {
        long now = Instant.now().toEpochMilli();
        updateWindowIfNeeded(now);
        return getEstimatedCount(now);
    }

    /**
     * Get window progress percentage (0-100)
     */
    public double getWindowProgress() {
        long now = Instant.now().toEpochMilli();
        long windowStart = currentWindowStart.get();
        double timeIntoWindow = now - windowStart;
        return (timeIntoWindow / windowSizeMillis) * 100.0;
    }

    /**
     * Get debug info
     */
    public String getDebugInfo() {
        return String.format(
                "Previous: %d | Current: %d | Estimated: %.2f | Progress: %.1f%%",
                previousWindowCount.get(),
                currentWindowCount.get(),
                getEstimatedCount(),
                getWindowProgress()
        );
    }
}