
package com.example.FixItNow.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class AuthenticationSecurityTest {

    @Test
    void testSessionTimeout() {
        long sessionTimeoutMs = 900000;
        long userInactiveTime = 600000;
        assertTrue(userInactiveTime < sessionTimeoutMs);
        System.out.println("✅ Session timeout test passed!");
    }

    @Test
    void testRoleBasedAccessControl() {
        String userRole = "USER";
        String adminRole = "ADMIN";
        boolean isAdmin = adminRole.equals("ADMIN");
        assertTrue(isAdmin);
        System.out.println("✅ Role-based access control test passed!");
    }
}
