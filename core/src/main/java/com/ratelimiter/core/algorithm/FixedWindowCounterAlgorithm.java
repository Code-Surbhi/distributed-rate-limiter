package com.ratelimiter.core.algorithm;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FixedWindowCounterAlgorithm {

    private final int maxRequests;              // Request limit per window
    private final long windowSizeMillis;        // Window size in milliseconds
    private final AtomicInteger counter;        // Request count in current window
    private final AtomicLong currentWindowStart; // Start time of current window

    public FixedWindowCounterAlgorithm(int maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.counter = new AtomicInteger(0);
        this.currentWindowStart = new AtomicLong(getCurrentWindow());
    }

    /**
     * Try to allow a request
     * @return true if request allowed, false if rate limited
     */
    public boolean allowRequest() {
        long now = Instant.now().toEpochMilli();
        long currentWindow = getCurrentWindow();
        long storedWindow = currentWindowStart.get();

        // Check if we've moved to a new window
        if (currentWindow > storedWindow) {
            // Reset counter for new window
            counter.set(0);
            currentWindowStart.set(currentWindow);
        }

        // Increment and check if we're within limit
        int count = counter.incrementAndGet();

        if (count <= maxRequests) {
            return true;  // Request allowed ✅
        } else {
            return false; // Rate limited ❌
        }
    }

    /**
     * Calculate which window we're currently in
     * @return window number
     */
    private long getCurrentWindow() {
        return Instant.now().toEpochMilli() / windowSizeMillis;
    }

    /**
     * Get remaining requests in current window
     */
    public int getRemainingRequests() {
        long currentWindow = getCurrentWindow();
        long storedWindow = currentWindowStart.get();

        // If we're in a new window, all requests are available
        if (currentWindow > storedWindow) {
            return maxRequests;
        }

        int used = counter.get();
        return Math.max(0, maxRequests - used);
    }

    /**
     * Get time until window resets (in milliseconds)
     */
    public long getTimeUntilReset() {
        long currentWindow = getCurrentWindow();
        long nextWindowStart = (currentWindow + 1) * windowSizeMillis;
        long now = Instant.now().toEpochMilli();
        return nextWindowStart - now;
    }
}