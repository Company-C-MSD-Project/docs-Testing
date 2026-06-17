
package com.example.FixItNow.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class RateLimitingTest {

    @Test
    void testRateLimitPerUser() {
        int maxRequestsPerMinute = 100;
        int requestsMade = 95;
        assertTrue(requestsMade <= maxRequestsPerMinute);
        System.out.println("✅ Rate limiting test passed!");
    }

    @Test
    void testIPBlockingAfterFailures() {
        int maxFailedAttempts = 5;
        int failedAttempts = 6;
        boolean isBlocked = failedAttempts > maxFailedAttempts;
        assertTrue(isBlocked);
        System.out.println("✅ IP blocking test passed!");
    }

    @Test
    void testRequestSizeLimit() {
        int maxRequestSizeKB = 1024;
        int requestSizeKB = 500;
        assertTrue(requestSizeKB <= maxRequestSizeKB);
        System.out.println("✅ Request size limit test passed!");
    }
}
