package com.ratelimiter.core.controller;

import com.ratelimiter.core.algorithm.SlidingWindowCounterAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class SlidingWindowCounterTestController {

    // 10 requests per 30 seconds
    private final SlidingWindowCounterAlgorithm algorithm =
            new SlidingWindowCounterAlgorithm(10, 30_000);

    @GetMapping("/sliding-counter")
    public String testSlidingCounter() {
        if (algorithm.allowRequest()) {
            return String.format(
                    "✅ Request allowed! | %s",
                    algorithm.getDebugInfo()
            );
        } else {
            return String.format(
                    "❌ Rate limited! | %s",
                    algorithm.getDebugInfo()
            );
        }
    }
}