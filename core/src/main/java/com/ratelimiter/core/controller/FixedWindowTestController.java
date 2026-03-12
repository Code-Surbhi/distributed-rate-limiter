package com.ratelimiter.core.controller;

import com.ratelimiter.core.algorithm.FixedWindowCounterAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class FixedWindowTestController {

    // Create: 10 requests per 30-second window
    private final FixedWindowCounterAlgorithm algorithm =
            new FixedWindowCounterAlgorithm(10, 30_000); // 30 seconds

    @GetMapping("/fixed-window")
    public String testFixedWindow() {
        if (algorithm.allowRequest()) {
            return String.format(
                    "✅ Request allowed! Remaining: %d | Reset in: %d seconds",
                    algorithm.getRemainingRequests(),
                    algorithm.getTimeUntilReset() / 1000
            );
        } else {
            return String.format(
                    "❌ Rate limited! Retry in: %d seconds",
                    algorithm.getTimeUntilReset() / 1000
            );
        }
    }
}