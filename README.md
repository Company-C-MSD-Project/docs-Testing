Security and Performance testing- FixItNow:::
overview:::
This repository contains the security testing and performance testing artifacts for the FixItNow project. these tests ensure that the application is secure against common web vulnerabilities and performant enough to handle real world user loads as specified in the software requirements specification.
In security testing,
1.Password Encoding: Passwords are hashed using BCrypt, not stored as plain text. 
2.SQL Injection Prevention: Malicious SQL code in inputs is detected and blocked. 
3.XSS Prevention: Malicious scripts in inputs are detected and blocked. 
4.Role-Based Access Control: Only ADMIN users can access admin endpoints. 
5.Session Security: Session IDs are unique; sessions expire after inactivity. 
6.Rate Limiting: Users cannot exceed 100 requests per minute. 
7.HTTPS/TLS Enforcement: Secure communication protocols are enforced. 
8.Data Masking: Sensitive data is hidden before display. 
is tested and all are passed.
In performance testing,
1.500 concurrent users: system handles 500 simulatneous users with 96%+ success rate.
2.Response time: Average response time stays under 2 seconds.
3.Memory leak detection: No significant memory increase after repeated operations.
4.Peak hour load: System remains stable during simulated peak traffic.
5.Stress test: System handles 1000+ users with 90%+ success rate.
6.Batch insert performance: Batch database operations complete within 5 seconds.
are tested and all tests are passed.
