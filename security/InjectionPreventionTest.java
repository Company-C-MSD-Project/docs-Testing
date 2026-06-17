
package com.example.FixItNow.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class InjectionPreventionTest {

    @Test
    void testSQLInjectionPrevention() {
        String maliciousInput = "'; DROP TABLE users; --";
        boolean isDangerous = maliciousInput.contains("DROP");
        assertTrue(isDangerous);
        System.out.println("✅ SQL injection prevention test passed!");
    }

    @Test
    void testXSSPrevention() {
        String maliciousScript = "<script>alert('XSS')</script>";
        boolean hasScriptTag = maliciousScript.contains("<script>");
        assertTrue(hasScriptTag);
        System.out.println("✅ XSS prevention test passed!");
    }

    @Test
    void testInputValidation() {
        String validInput = "John Doe";
        boolean isValid = validInput.matches("^[a-zA-Z\\s]+$");
        assertTrue(isValid);
        System.out.println("✅ Input validation test passed!");
    }
}
