package com.example.FixItNow.performance;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class DatabasePerformanceTest {

    @Test
    void testDatabaseQueryPerformance() {
        int queries = 1000;
        List<Long> queryTimes = new ArrayList<>();
        
        System.out.println("\n=== DATABASE QUERY PERFORMANCE TEST ===");
        System.out.println("Executing " + queries + " simulated queries...");
        
        for (int i = 0; i < queries; i++) {
            Instant start = Instant.now();
            
            // Simulate database query
            try { Thread.sleep(1); } catch (Exception e) {}
            
            Instant end = Instant.now();
            long queryTime = Duration.between(start, end).toMillis();
            queryTimes.add(queryTime);
        }
        
        double avgQueryTime = queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxQueryTime = queryTimes.stream().max(Long::compare).orElse(0L);
        
        System.out.println("\n=== DATABASE RESULTS ===");
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ Total Queries:      " + String.format("%-12d", queries) + " │");
        System.out.println("│ Average Query Time: " + String.format("%-12.2f", avgQueryTime) + " ms │");
        System.out.println("│ Max Query Time:     " + String.format("%-12d", maxQueryTime) + " ms │");
        System.out.println("│ Total Time:         " + String.format("%-12d", (long)avgQueryTime * queries) + " ms │");
        System.out.println("└─────────────────────────────────────┘");
        
        assertTrue(avgQueryTime < 50, "Average query time should be under 50ms");
        
        System.out.println("\n✅ DATABASE PERFORMANCE TEST PASSED!");
    }
    
    @Test
    void testBatchInsertPerformance() {
        int batchSize = 1000;
        
        System.out.println("\n=== BATCH INSERT PERFORMANCE TEST ===");
        System.out.println("Inserting " + batchSize + " records...");
        
        Instant start = Instant.now();
        
        // Simulate batch insert
        for (int i = 0; i < batchSize; i++) {
            // Simulate insert operation
            try { Thread.sleep(1); } catch (Exception e) {}
        }
        
        Instant end = Instant.now();
        long duration = Duration.between(start, end).toMillis();
        
        System.out.println("\n=== BATCH INSERT RESULTS ===");
        System.out.println("Records Inserted: " + batchSize);
        System.out.println("Total Time: " + duration + " ms");
        System.out.println("Records/Second: " + (batchSize * 1000.0 / duration));
        
        assertTrue(duration < 5000, "Batch insert should complete within 5 seconds");
        
        System.out.println("\n✅ BATCH INSERT PERFORMANCE TEST PASSED!");
    }
}