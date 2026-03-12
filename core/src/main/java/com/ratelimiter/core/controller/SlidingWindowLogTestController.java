package com.ratelimiter.core.controller;

import com.ratelimiter.core.algorithm.SlidingWindowLogAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/test")
public class SlidingWindowLogTestController {

    // 10 requests per 30 seconds
    private final SlidingWindowLogAlgorithm algorithm =
            new SlidingWindowLogAlgorithm(10, 30_000);

    @GetMapping("/sliding-log")
    public String testSlidingLog() {
        long now = Instant.now().toEpochMilli();
        Long oldestTimestamp = algorithm.getOldestTimestamp();

        if (algorithm.allowRequest()) {
            String oldestInfo = oldestTimestamp != null
                    ? String.format("Oldest request: %d ms ago", now - oldestTimestamp)
                    : "First request in window";

            return String.format(
                    "✅ Request allowed! | Current count: %d/%d | %s",
                    algorithm.getCurrentCount(),
                    10,
                    oldestInfo
            );
        } else {
            return String.format(
                    "❌ Rate limited! | Window full: %d/%d | Oldest expires in: %d ms",
                    algorithm.getCurrentCount(),
                    10,
                    oldestTimestamp != null ? (oldestTimestamp + 30_000 - now) : 0
            );
        }
    }
}