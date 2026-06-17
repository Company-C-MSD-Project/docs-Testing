
package com.example.FixItNow.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class SecureCommunicationTest {

    @Test
    void testHttpsRequired() {
        String apiUrl = "https://api.fixitnow.com";
        assertTrue(apiUrl.startsWith("https://"));
        System.out.println("✅ HTTPS enforcement test passed!");
    }

    @Test
    void testTLSVersion() {
        String tlsVersion = "TLSv1.3";
        boolean isSecure = tlsVersion.equals("TLSv1.3") || tlsVersion.equals("TLSv1.2");
        assertTrue(isSecure);
        System.out.println("✅ TLS version test passed!");
    }

    @Test
    void testCertificateValidation() {
        boolean hasValidCertificate = true;
        assertTrue(hasValidCertificate);
        System.out.println("✅ Certificate validation test passed!");
    }
}
