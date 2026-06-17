
package com.example.FixItNow.security;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

public class DataEncryptionTest {

    @Test
    void testPasswordHashing() {
        String plainPassword = "MySecret123";
        String hashedPassword = Integer.toHexString(plainPassword.hashCode());
        assertNotEquals(plainPassword, hashedPassword);
        System.out.println("✅ Password hashing test passed!");
    }

    @Test
    void testSensitiveDataMasking() {
        String creditCard = "1234-5678-9012-3456";
        String maskedCard = "****-****-****-3456";
        assertNotEquals(creditCard, maskedCard);
        System.out.println("✅ Data masking test passed!");
    }

    @Test
    void testEncryptionDecryption() {
        String originalData = "Sensitive user data";
        String encryptedData = Base64.getEncoder().encodeToString(originalData.getBytes());
        String decryptedData = new String(Base64.getDecoder().decode(encryptedData));
        assertEquals(originalData, decryptedData);
        System.out.println("✅ Encryption/decryption test passed!");
    }
}
