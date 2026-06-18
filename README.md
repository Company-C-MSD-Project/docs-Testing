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

## API Testing with Postman

#Test Coverage
The Postman collection includes comprehensive API tests covering:

| Category | Number of Tests | Status |

| Authentication | 8 | passed|
| Admin Setup | 4 | passed|
| Categories & Services | 4 | passed |
| Smart Match | 2 | passed|
| Bookings | 9 | passed|
| Payments | 6 | passed|
| Reviews | 3 | passed|
| Notifications | 3 | passed|
| Disputes | 6 | passed |
Total 45 test cases are passed.

Detailed Test Breakdown
1. Authentication Tests (8 tests)
-  Register Homeowner
-  Register Service Provider
-  Register Admin
-  Register Duplicate Email (should fail)
-  Login Homeowner
-  Login Wrong Password (should fail)
-  Login Provider
-  Login Admin

 2. Admin Setup Tests (4 tests)
-  Verify Provider
-  Get Pending Providers
-  Get Analytics
-  Admin Endpoint Without Token (should fail)

 3. Categories & Services Tests (4 tests)
-  Create Category (Admin)
-  Get All Categories
-  Create Service Under Category (Admin)
-  Get All Services

4. Smart Match Tests (2 tests)
-  Find Matching Providers
-  Match Without Token (should fail)

5. Bookings Tests (9 tests)
-  Create Booking (Homeowner)
-  Create Booking as Provider (should fail)
-  Get Booking By Id
-  Accept Booking (Provider)
-  Accept Already-Accepted Booking (should fail)
-  Start Job (Provider)
-  Complete Job (Provider)
-  Get Bookings By Homeowner
-  Get Bookings By Provider

6. Payments Tests (6 tests)
-  Initiate Payment (Homeowner)
-  Initiate Duplicate Payment (should fail)
-  Confirm Payment (Stripe webhook)
-  Get Payment By Booking
-  Refund Payment (Admin)
-  Refund as Homeowner (should fail)

7. Reviews Tests (3 tests)
- Submit Review (Homeowner)
- Submit Duplicate Review (should fail)
- Get Provider Reviews

8. Notifications Tests (3 tests)
-  Get Notifications For Homeowner
-  Get Unread Notifications
-  Mark Notification As Read

9. Disputes Tests (6 tests)
-  Raise Dispute (Homeowner)
-  Raise Duplicate Open Dispute (should fail)
-  Escalate Dispute
-  Resolve Dispute (Admin)
-  Resolve Dispute as Homeowner (should fail)
-  Get All Disputes (Admin)

# Prerequisites
- [Postman](https://www.postman.com/downloads/) installed (v10+)
- Backend server running locally or accessible via URL
- Stripe test keys configured on the backend (for payment tests)
- Database seeded with initial test data (or run registration tests first)

#Environment Variables Used
| Variable | Description | Example Value |

| `baseUrl` | API Base URL | `http://localhost:8080` |
| `homeownerEmail` | Test homeowner email | `alice.123@fixitnow.test` |
| `providerEmail` | Test provider email | `bob.456@fixitnow.test` |
| `adminEmail` | Test admin email | `carol.789@fixitnow.test` |
| `homeownerToken` | JWT Token (auto-set) | Auto-generated on login |
| `providerToken` | JWT Token (auto-set) | Auto-generated on login |
| `adminToken` | JWT Token (auto-set) | Auto-generated on login |
| `homeownerId` | User ID (auto-set) | Auto-set after registration |
| `providerId` | User ID (auto-set) | Auto-set after registration |
| `adminId` | User ID (auto-set) | Auto-set after registration |
| `categoryId` | Category ID (auto-set) | Auto-set after creation |
| `serviceId` | Service ID (auto-set) | Auto-set after creation |
| `bookingId` | Booking ID (auto-set) | Auto-set after creation |
| `paymentId` | Payment ID (auto-set) | Auto-set after initiation |
| `stripeIntentId` | Stripe Payment Intent | Auto-set on payment initiation |
| `notificationId` | Notification ID (auto-set) | Auto-set from first notification |

The collection uses variable chaining - later requests reuse IDs/tokens saved automatically by earlier ones.
