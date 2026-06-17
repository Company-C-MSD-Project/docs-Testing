
package com.example.FixItNow.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class SecurityTest {

    @Test
    void testPasswordEncoding() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Password123!";
        String encodedPassword = encoder.encode(rawPassword);
        
        // Test that encoded password is different from raw
        assertNotEquals(rawPassword, encodedPassword);
        
        // Test that raw password matches encoded
        assertTrue(encoder.matches(rawPassword, encodedPassword));
        
        System.out.println("✅ Password encoding test passed!");
    }

    @Test
    void testEmailValidation() {
        String validEmail = "user@example.com";
        String invalidEmail = "not-an-email";
        
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        
        assertTrue(validEmail.matches(emailRegex));
        assertFalse(invalidEmail.matches(emailRegex));
        
        System.out.println("✅ Email validation test passed!");
    }

    @Test
    void testPasswordStrength() {
        String weakPassword = "123";
        String strongPassword = "Password123!@#";
        
        boolean isStrong = strongPassword.length() >= 8 && 
                          strongPassword.matches(".*[A-Z].*") &&
                          strongPassword.matches(".*[a-z].*") &&
                          strongPassword.matches(".*[0-9].*");
        
        assertTrue(isStrong);
        assertFalse(weakPassword.length() >= 8);
        
        System.out.println("✅ Password strength test passed!");
    }
    
    @Test
    void testPhoneNumberValidation() {
        String validPhone = "+1234567890";
        String invalidPhone = "abc123";
        
        String phoneRegex = "^\\+?[0-9]{10,15}$";
        
        assertTrue(validPhone.matches(phoneRegex));
        assertFalse(invalidPhone.matches(phoneRegex));
        
        System.out.println("✅ Phone validation test passed!");
    }
}
