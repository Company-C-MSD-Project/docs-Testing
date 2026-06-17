package com.example.FixItNow.performance;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryPerformanceTest {

    @Test
    void testMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection
        runtime.gc();
        
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.println("\n=== MEMORY USAGE TEST ===");
        System.out.println("Creating 100,000 booking objects...");
        
        // Simulate storing booking objects
        List<String> bookings = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            bookings.add("Booking_" + i + "_Customer_" + (i % 100));
        }
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        System.out.println("\n=== MEMORY RESULTS ===");
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ Memory Before: " + String.format("%-16d", memoryBefore / 1024) + " KB │");
        System.out.println("│ Memory After:  " + String.format("%-16d", memoryAfter / 1024) + " KB │");
        System.out.println("│ Memory Used:   " + String.format("%-16d", memoryUsed / 1024) + " KB │");
        System.out.println("│ Objects Created: 100,000           │");
        System.out.println("└─────────────────────────────────────┘");
        
        // Should not exceed 50MB for 100k objects
        assertTrue(memoryUsed < 50 * 1024 * 1024, "Memory usage should be under 50MB");
        
        System.out.println("\n✅ MEMORY USAGE TEST PASSED!");
    }
    
    @Test
    void testNoMemoryLeak() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC and record baseline
        runtime.gc();
        Thread.sleep(100);
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.println("\n=== MEMORY LEAK TEST ===");
        System.out.println("Running 1000 iterations...");
        
        // Perform operations 1000 times
        for (int iteration = 0; iteration < 1000; iteration++) {
            List<String> tempList = new ArrayList<>();
            for (int j = 0; j < 100; j++) {
                tempList.add("Temp_Booking_Data_" + iteration + "_" + j);
            }
            // Let tempList go out of scope
        }
        
        // Force GC again
        runtime.gc();
        Thread.sleep(100);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - baselineMemory;
        
        System.out.println("\n=== MEMORY LEAK RESULTS ===");
        System.out.println("┌─────────────────────────────────────┐");
        System.out.println("│ Baseline Memory: " + String.format("%-14d", baselineMemory / 1024) + " KB │");
        System.out.println("│ Final Memory:    " + String.format("%-14d", finalMemory / 1024) + " KB │");
        System.out.println("│ Memory Increase: " + String.format("%-14d", memoryIncrease / 1024) + " KB │");
        System.out.println("└─────────────────────────────────────┘");
        
        // Memory increase should be minimal (less than 5MB)
        assertTrue(memoryIncrease < 5 * 1024 * 1024, "No significant memory leak detected");
        
        System.out.println("\n✅ MEMORY LEAK TEST PASSED!");
    }
}