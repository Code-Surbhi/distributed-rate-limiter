package com.ratelimiter.core.controller;

import com.ratelimiter.core.model.RateLimitResponse;
import com.ratelimiter.core.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/benchmark")
@Slf4j
public class BenchmarkController {

    @Autowired
    private RateLimiterService rateLimiterService;

    /**
     * Run a simple benchmark
     *
     * Usage: GET /api/benchmark/run?requests=1000&threads=10
     */
    @GetMapping("/run")
    public BenchmarkResult runBenchmark(
            @RequestParam(defaultValue = "1000") int requests,
            @RequestParam(defaultValue = "10") int threads) throws InterruptedException {

        log.info("Starting benchmark: {} requests with {} threads", requests, threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(requests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rateLimitedCount = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        long startTime = System.currentTimeMillis();

        // Submit all requests
        for (int i = 0; i < requests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    long requestStart = System.nanoTime();

                    RateLimitResponse response = rateLimiterService.checkRateLimit(
                            "bench-user-" + (requestId % 1000), // 1000 different users (spread the load)
                            "enterprise-tier" // Higher limits
                    );

                    long requestEnd = System.nanoTime();
                    long latencyMs = (requestEnd - requestStart) / 1_000_000; // Convert to ms

                    latencies.add(latencyMs);

                    if (response.isAllowed()) {
                        successCount.incrementAndGet();
                    } else {
                        rateLimitedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Request failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all requests to complete
        latch.await();
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;

        // Calculate statistics
        latencies.sort(Long::compareTo);

        return BenchmarkResult.builder()
                .totalRequests(requests)
                .threads(threads)
                .successCount(successCount.get())
                .rateLimitedCount(rateLimitedCount.get())
                .totalTimeMs(totalTimeMs)
                .requestsPerSecond((requests * 1000.0) / totalTimeMs)
                .avgLatencyMs(latencies.stream().mapToLong(Long::longValue).average().orElse(0))
                .p50LatencyMs(getPercentile(latencies, 0.50))
                .p95LatencyMs(getPercentile(latencies, 0.95))
                .p99LatencyMs(getPercentile(latencies, 0.99))
                .minLatencyMs(latencies.isEmpty() ? 0 : latencies.get(0))
                .maxLatencyMs(latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1))
                .build();
    }

    private long getPercentile(List<Long> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedList.size()) - 1;
        return sortedList.get(Math.max(0, index));
    }

    /**
     * Benchmark result
     */
    public static class BenchmarkResult {
        public int totalRequests;
        public int threads;
        public int successCount;
        public int rateLimitedCount;
        public long totalTimeMs;
        public double requestsPerSecond;
        public double avgLatencyMs;
        public long p50LatencyMs;
        public long p95LatencyMs;
        public long p99LatencyMs;
        public long minLatencyMs;
        public long maxLatencyMs;

        public static BenchmarkResultBuilder builder() {
            return new BenchmarkResultBuilder();
        }

        public static class BenchmarkResultBuilder {
            private final BenchmarkResult result = new BenchmarkResult();

            public BenchmarkResultBuilder totalRequests(int val) { result.totalRequests = val; return this; }
            public BenchmarkResultBuilder threads(int val) { result.threads = val; return this; }
            public BenchmarkResultBuilder successCount(int val) { result.successCount = val; return this; }
            public BenchmarkResultBuilder rateLimitedCount(int val) { result.rateLimitedCount = val; return this; }
            public BenchmarkResultBuilder totalTimeMs(long val) { result.totalTimeMs = val; return this; }
            public BenchmarkResultBuilder requestsPerSecond(double val) { result.requestsPerSecond = val; return this; }
            public BenchmarkResultBuilder avgLatencyMs(double val) { result.avgLatencyMs = val; return this; }
            public BenchmarkResultBuilder p50LatencyMs(long val) { result.p50LatencyMs = val; return this; }
            public BenchmarkResultBuilder p95LatencyMs(long val) { result.p95LatencyMs = val; return this; }
            public BenchmarkResultBuilder p99LatencyMs(long val) { result.p99LatencyMs = val; return this; }
            public BenchmarkResultBuilder minLatencyMs(long val) { result.minLatencyMs = val; return this; }
            public BenchmarkResultBuilder maxLatencyMs(long val) { result.maxLatencyMs = val; return this; }

            public BenchmarkResult build() { return result; }
        }
    }
}