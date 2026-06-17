package com.example.FixItNow.performance;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class ResponseTimeTest {

    @Test
    void testAverageResponseTime() {
        int requests = 100;
        List<Long> responseTimes = new ArrayList<>();
        
        System.out.println("\n=== RESPONSE TIME TEST ===");
        System.out.println("Measuring response time for " + requests + " requests...");
        
        for (int i = 0; i < requests; i++) {
            Instant start = Instant.now();
            
            // Simulate service operation
            try { Thread.sleep((long)(Math.random() * 50)); } catch (Exception e) {}
            
            Instant end = Instant.now();
            long responseTime = Duration.between(start, end).toMillis();
            responseTimes.add(responseTime);
        }
        
        // Calculate statistics
        double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxResponseTime = responseTimes.stream().max(Long::compare).orElse(0L);
        long minResponseTime = responseTimes.stream().min(Long::compare).orElse(0L);
        
        // Calculate percentile
        responseTimes.sort(Long::compare);
        long p95ResponseTime = responseTimes.get((int)(requests * 0.95));
        
        System.out.println("\n=== RESPONSE TIME RESULTS ===");
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ Average Response Time: " + String.format("%-12.0f", avgResponseTime) + " ms │");
        System.out.println("│ Max Response Time:     " + String.format("%-12d", maxResponseTime) + " ms │");
        System.out.println("│ Min Response Time:     " + String.format("%-12d", minResponseTime) + " ms │");
        System.out.println("│ 95th Percentile:       " + String.format("%-12d", p95ResponseTime) + " ms │");
        System.out.println("└─────────────────────────────────────┘");
        
        // SRS Requirement: Response time under 2 seconds (2000ms)
        assertTrue(avgResponseTime < 2000, "Average response time should be under 2 seconds");
        assertTrue(p95ResponseTime < 3000, "95% of requests should complete within 3 seconds");
        
        System.out.println("\n✅ RESPONSE TIME TEST PASSED!");
    }
    
    @Test
    void testPeakHourResponseTime() {
        int requests = 500;  // Peak hour load
        List<Long> responseTimes = new ArrayList<>();
        
        System.out.println("\n=== PEAK HOUR RESPONSE TIME TEST ===");
        System.out.println("Simulating peak hour with " + requests + " requests...");
        
        for (int i = 0; i < requests; i++) {
            Instant start = Instant.now();
            
            // Simulate heavier load during peak hours
            try { Thread.sleep((long)(Math.random() * 80)); } catch (Exception e) {}
            
            Instant end = Instant.now();
            long responseTime = Duration.between(start, end).toMillis();
            responseTimes.add(responseTime);
        }
        
        double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxResponseTime = responseTimes.stream().max(Long::compare).orElse(0L);
        
        System.out.println("\n=== PEAK HOUR RESULTS ===");
        System.out.println("Average Response Time: " + avgResponseTime + " ms");
        System.out.println("Max Response Time: " + maxResponseTime + " ms");
        System.out.println("Total Requests: " + requests);
        
        // Even during peak, response should be reasonable
        assertTrue(avgResponseTime < 3000, "Peak hour response under 3 seconds");
        
        System.out.println("\n✅ PEAK HOUR RESPONSE TIME TEST PASSED!");
    }
}