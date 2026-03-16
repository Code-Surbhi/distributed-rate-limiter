package com.ratelimiter.core.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@DisplayName("Redis Lua Sliding Window Counter - Integration Tests")
class RedisLuaSlidingWindowCounterRateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private RedisLuaSlidingWindowCounterRateLimiter rateLimiter;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear Redis before each test
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Should allow requests within limit")
    void shouldAllowRequestsWithinLimit() {
        // Given: Limit of 10 requests per 30 seconds
        String userId = "user-test-1";
        int maxRequests = 10;
        long windowSizeMillis = 30_000;
        int ttl = 300;

        // When: Make 10 requests
        for (int i = 0; i < 10; i++) {
            boolean allowed = rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);

            // Then: All should be allowed
            assertThat(allowed)
                    .withFailMessage("Request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding limit")
    void shouldRejectRequestsExceedingLimit() {
        // Given: Limit of 5 requests per 10 seconds
        String userId = "user-test-2";
        int maxRequests = 5;
        long windowSizeMillis = 10_000;
        int ttl = 300;

        // And: Exhaust the limit
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        }

        // When: Try 6th request
        boolean allowed = rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);

        // Then: Should be rejected
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("Should isolate different users")
    void shouldIsolateDifferentUsers() {
        // Given: Two different users
        String user1 = "alice";
        String user2 = "bob";
        int maxRequests = 5;
        long windowSizeMillis = 10_000;
        int ttl = 300;

        // When: User 1 exhausts their limit
        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(user1, maxRequests, windowSizeMillis, ttl);
        }

        // Then: User 1 should be rate limited
        assertThat(rateLimiter.allowRequest(user1, maxRequests, windowSizeMillis, ttl))
                .isFalse();

        // But: User 2 should still have full quota
        assertThat(rateLimiter.allowRequest(user2, maxRequests, windowSizeMillis, ttl))
                .isTrue();
    }

    @Test
    @DisplayName("Should calculate accurate estimated count")
    void shouldCalculateAccurateEstimatedCount() {
        // Given: User makes 3 requests
        String userId = "user-test-3";
        int maxRequests = 10;
        long windowSizeMillis = 10_000;
        int ttl = 300;

        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        }

        // When: Check estimated count
        double estimated = rateLimiter.getEstimatedCount(userId, windowSizeMillis);

        // Then: Should be approximately 3
        assertThat(estimated).isBetween(2.8, 3.2);
    }

    @Test
    @DisplayName("Should handle window rotation correctly")
    void shouldHandleWindowRotationCorrectly() throws InterruptedException {
        // Given: User makes requests in first window
        String userId = "user-test-4";
        int maxRequests = 10;
        long windowSizeMillis = 5_000; // 5 second window for faster test
        int ttl = 300;

        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        }

        // When: Wait for window to fully rotate
        Thread.sleep(6_000);

        // Then: Should be able to make new requests
        boolean allowed = rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        assertThat(allowed).isTrue();

        // And: Debug info should show window rotation
        String debugInfo = rateLimiter.getDebugInfo(userId, windowSizeMillis);
        assertThat(debugInfo).contains("Current:");
    }

    @Test
    @DisplayName("Should show previous and current window counts after rotation")
    void shouldShowPreviousAndCurrentWindowCountsAfterRotation() throws InterruptedException {
        // Given: Make 5 requests in first window
        String userId = "user-test-5";
        int maxRequests = 10;
        long windowSizeMillis = 5_000; // 5 second window
        int ttl = 300;

        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        }

        // When: Check debug info in first window
        String debugInfo1 = rateLimiter.getDebugInfo(userId, windowSizeMillis);
        assertThat(debugInfo1).contains("Current: 5");

        // And: Wait for window to rotate (6 seconds > 5 second window)
        Thread.sleep(6_000);

        // And: Make 2 new requests in new window
        rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);

        // Then: Debug info should show window rotation happened
        String debugInfo2 = rateLimiter.getDebugInfo(userId, windowSizeMillis);

        // Previous should now contain old window's count
        assertThat(debugInfo2)
                .withFailMessage("Expected to see Previous count from rotated window, got: %s", debugInfo2)
                .contains("Previous:");

        // Current should show new window's requests
        assertThat(debugInfo2)
                .withFailMessage("Expected Current: 2 in new window, got: %s", debugInfo2)
                .contains("Current: 2");

        // Estimated should be calculated (exact value depends on timing, just verify it exists)
        assertThat(debugInfo2).contains("Estimated:");
    }

    @Test
    @DisplayName("Should handle concurrent-like rapid requests atomically")
    void shouldHandleRapidRequestsAtomically() {
        // Given: High request limit
        String userId = "user-test-6";
        int maxRequests = 100;
        long windowSizeMillis = 60_000;
        int ttl = 300;

        // When: Make many rapid requests
        int allowedCount = 0;
        for (int i = 0; i < 150; i++) {
            if (rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl)) {
                allowedCount++;
            }
        }

        // Then: Should allow exactly 100 (no race conditions thanks to Lua)
        assertThat(allowedCount).isEqualTo(100);
    }

    @Test
    @DisplayName("Should persist data in Redis")
    void shouldPersistDataInRedis() {
        // Given: Make requests
        String userId = "user-test-7";
        int maxRequests = 10;
        long windowSizeMillis = 30_000;
        int ttl = 300;

        for (int i = 0; i < 3; i++) {
            rateLimiter.allowRequest(userId, maxRequests, windowSizeMillis, ttl);
        }

        // When: Check Redis keys exist
        String previousKey = "ratelimit:slidingcounter:" + userId + ":previous";
        String currentKey = "ratelimit:slidingcounter:" + userId + ":current";
        String windowStartKey = "ratelimit:slidingcounter:" + userId + ":windowStart";

        // Then: Keys should exist in Redis
        assertThat(redisTemplate.hasKey(currentKey)).isTrue();
        assertThat(redisTemplate.hasKey(windowStartKey)).isTrue();

        // And: Current count should be 3
        Object currentCount = redisTemplate.opsForValue().get(currentKey);
        assertThat(currentCount).isNotNull();
        assertThat(Integer.parseInt(currentCount.toString())).isEqualTo(3);
    }
}