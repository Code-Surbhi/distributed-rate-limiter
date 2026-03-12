package com.ratelimiter.core.algorithm;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

public class SlidingWindowLogAlgorithm {

    private final int maxRequests;              // Request limit per window
    private final long windowSizeMillis;        // Window size in milliseconds
    private final ConcurrentLinkedDeque<Long> requestLog; // Thread-safe deque

    public SlidingWindowLogAlgorithm(int maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.requestLog = new ConcurrentLinkedDeque<>();
    }

    /**
     * Try to allow a request
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest() {
        long now = Instant.now().toEpochMilli();

        // Remove timestamps outside the current window
        removeOldTimestamps(now);

        // Check if we're within the limit
        if (requestLog.size() < maxRequests) {
            requestLog.addLast(now);
            return true;  // Request allowed ✅
        }

        return false;  // Rate limited ❌
    }

    /**
     * Remove timestamps that are older than the window
     */
    private void removeOldTimestamps(long currentTime) {
        long windowStart = currentTime - windowSizeMillis;

        // Remove from front of deque while timestamps are too old
        while (!requestLog.isEmpty() && requestLog.peekFirst() < windowStart) {
            requestLog.removeFirst();
        }
    }

    /**
     * Get remaining requests in current window
     */
    public int getRemainingRequests() {
        long now = Instant.now().toEpochMilli();
        removeOldTimestamps(now);
        return Math.max(0, maxRequests - requestLog.size());
    }

    /**
     * Get current request count in window
     */
    public int getCurrentCount() {
        long now = Instant.now().toEpochMilli();
        removeOldTimestamps(now);
        return requestLog.size();
    }

    /**
     * Get oldest timestamp in the window (for debugging)
     */
    public Long getOldestTimestamp() {
        return requestLog.peekFirst();
    }
}