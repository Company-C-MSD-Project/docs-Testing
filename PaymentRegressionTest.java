package com.example.FixItNow.regression;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.FixItNow.entity.Booking;
import com.example.FixItNow.entity.Payment;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BookingStatus;
import com.example.FixItNow.enums.PaymentStatus;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.BookingRepository;
import com.example.FixItNow.repository.PaymentRepository;
import com.example.FixItNow.service.NotificationService;
import com.example.FixItNow.service.PaymentService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Payment Processing (SRS FR10)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that payment confirmation, guards, notifications,
 *   refund logic, and invoice fields continue to work correctly
 *   after any code update.
 *
 *   Note: Stripe API calls (PaymentIntent.create, Refund.create)
 *   make real network calls and cannot be mocked as static calls
 *   without a Stripe mock library. Those paths (initiatePayment
 *   Stripe call, refund Stripe call) are tested only for their
 *   service-layer GUARDS — the checks that run BEFORE Stripe is
 *   ever reached. This is the same approach used in the existing
 *   PaymentServiceTest in this project.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.PaymentRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/PaymentRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-P01  initiatePayment() blocked for non-COMPLETED booking
 *   RT-P02  initiatePayment() blocked when payment already exists
 *   RT-P03  initiatePayment() on unknown bookingId throws ResourceNotFoundException
 *   RT-P04  confirmPayment() sets status to COMPLETED
 *   RT-P05  confirmPayment() stamps paidAt timestamp
 *   RT-P06  confirmPayment() sets transactionRef starting with "TXN-"
 *   RT-P07  confirmPayment() sends PAYMENT_COMPLETED notification to homeowner
 *   RT-P08  confirmPayment() sends PAYMENT_RECEIVED notification to provider
 *   RT-P09  confirmPayment() saves the updated payment record
 *   RT-P10  confirmPayment() on unknown intentId throws ResourceNotFoundException
 *   RT-P11  refund() blocked for non-COMPLETED payment
 *   RT-P12  refund() blocked for PENDING payment
 *   RT-P13  refund() blocked for already-REFUNDED payment
 *   RT-P14  refund() on unknown paymentId throws ResourceNotFoundException
 *   RT-P15  getByBookingId() returns payment when found
 *   RT-P16  getByBookingId() throws ResourceNotFoundException when not found
 *   RT-P17  Payment default method is "STRIPE"
 *   RT-P18  Payment default status is PENDING on creation
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Payment Regression — Payment processing still works after updates")
class PaymentRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private PaymentRepository   paymentRepository;
    @Mock private BookingRepository   bookingRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private PaymentService paymentService;

    // ---------------------------------------------------------------- fixture
    private User    homeowner;
    private User    provider;
    private Booking completedBooking;
    private Booking pendingBooking;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        // Inject dummy Stripe key so @PostConstruct does not fail
        ReflectionTestUtils.setField(paymentService, "stripeSecretKey", "sk_test_dummy");

        homeowner = User.builder()
                .id(1L).name("Alice").email("alice@fixitnow.test")
                .username("alice").passwordHash("hashed")
                .userType(UserType.HOMEOWNER).build();

        provider = User.builder()
                .id(2L).name("Bob").email("bob@fixitnow.test")
                .username("bob").passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER).build();

        completedBooking = Booking.builder()
                .id(50L).homeowner(homeowner).provider(provider)
                .status(BookingStatus.COMPLETED)
                .estimatedCost(new BigDecimal("3500.00"))
                .scheduledDate(LocalDateTime.now().minusDays(1))
                .build();

        pendingBooking = Booking.builder()
                .id(51L).homeowner(homeowner).provider(provider)
                .status(BookingStatus.PENDING)
                .estimatedCost(new BigDecimal("3500.00"))
                .scheduledDate(LocalDateTime.now().plusDays(2))
                .build();

        pendingPayment = Payment.builder()
                .id(1L).booking(completedBooking)
                .amount(new BigDecimal("3500.00"))
                .stripePaymentIntent("pi_test_abc123")
                .status(PaymentStatus.PENDING)
                .build();
    }

    // ===================================================================
    // RT-P01 — initiatePayment() blocked for non-COMPLETED booking
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-P01 | initiatePayment() throws BadRequestException for a non-COMPLETED booking")
    void rtP01_initiatePayment_nonCompletedBooking_throwsBadRequestException() {
        when(bookingRepository.findById(51L)).thenReturn(Optional.of(pendingBooking));

        assertThatThrownBy(() -> paymentService.initiatePayment(51L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed bookings");
    }

    // ===================================================================
    // RT-P02 — initiatePayment() blocked when payment already exists
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-P02 | initiatePayment() throws BadRequestException if payment already initiated")
    void rtP02_initiatePayment_duplicatePayment_throwsBadRequestException() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(paymentRepository.findByBookingId(50L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.initiatePayment(50L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already initiated");

        verify(paymentRepository, never()).save(any());
    }

    // ===================================================================
    // RT-P03 — initiatePayment() on unknown bookingId
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-P03 | initiatePayment() throws ResourceNotFoundException for unknown bookingId")
    void rtP03_initiatePayment_unknownBookingId_throwsResourceNotFoundException() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.initiatePayment(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    // ===================================================================
    // RT-P04 — confirmPayment() sets status to COMPLETED
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-P04 | confirmPayment() sets payment status to COMPLETED")
    void rtP04_confirmPayment_setsStatusToCompleted() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.confirmPayment("pi_test_abc123");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    // ===================================================================
    // RT-P05 — confirmPayment() stamps paidAt timestamp
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-P05 | confirmPayment() sets a non-null paidAt timestamp")
    void rtP05_confirmPayment_stampsPaidAt() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.confirmPayment("pi_test_abc123");

        assertThat(result.getPaidAt())
                .as("paidAt must be set when payment is confirmed")
                .isNotNull();
    }

    // ===================================================================
    // RT-P06 — confirmPayment() sets transactionRef starting with "TXN-"
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-P06 | confirmPayment() sets transactionRef with 'TXN-' prefix")
    void rtP06_confirmPayment_setsTransactionRefWithTxnPrefix() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.confirmPayment("pi_test_abc123");

        assertThat(result.getTransactionRef())
                .as("transactionRef must start with TXN-")
                .isNotNull()
                .startsWith("TXN-");
    }

    // ===================================================================
    // RT-P07 — confirmPayment() sends PAYMENT_COMPLETED to homeowner
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-P07 | confirmPayment() sends PAYMENT_COMPLETED notification to homeowner")
    void rtP07_confirmPayment_sendsPaymentCompletedToHomeowner() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmPayment("pi_test_abc123");

        verify(notificationService).send(eq(homeowner), eq("PAYMENT_COMPLETED"), anyString());
    }

    // ===================================================================
    // RT-P08 — confirmPayment() sends PAYMENT_RECEIVED to provider
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-P08 | confirmPayment() sends PAYMENT_RECEIVED notification to provider")
    void rtP08_confirmPayment_sendsPaymentReceivedToProvider() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmPayment("pi_test_abc123");

        verify(notificationService).send(eq(provider), eq("PAYMENT_RECEIVED"), anyString());
    }

    // ===================================================================
    // RT-P09 — confirmPayment() saves the updated payment record
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-P09 | confirmPayment() persists the updated payment to the repository")
    void rtP09_confirmPayment_savesUpdatedPayment() {
        when(paymentRepository.findByStripePaymentIntent("pi_test_abc123"))
                .thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.confirmPayment("pi_test_abc123");

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    // ===================================================================
    // RT-P10 — confirmPayment() on unknown intentId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-P10 | confirmPayment() throws ResourceNotFoundException for unknown intentId")
    void rtP10_confirmPayment_unknownIntentId_throwsResourceNotFoundException() {
        when(paymentRepository.findByStripePaymentIntent("pi_unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPayment("pi_unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No payment found for intent");
    }

    // ===================================================================
    // RT-P11 — refund() blocked for non-COMPLETED payment (FAILED)
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-P11 | refund() throws BadRequestException for a FAILED payment")
    void rtP11_refund_failedPayment_throwsBadRequestException() {
        Payment failedPayment = Payment.builder()
                .id(2L).booking(completedBooking)
                .amount(new BigDecimal("3500.00"))
                .status(PaymentStatus.FAILED)
                .stripePaymentIntent("pi_failed_xyz")
                .build();

        when(paymentRepository.findById(2L)).thenReturn(Optional.of(failedPayment));

        assertThatThrownBy(() -> paymentService.refund(2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only completed payments can be refunded");
    }

    // ===================================================================
    // RT-P12 — refund() blocked for PENDING payment
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-P12 | refund() throws BadRequestException for a PENDING payment")
    void rtP12_refund_pendingPayment_throwsBadRequestException() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.refund(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only completed payments can be refunded");
    }

    // ===================================================================
    // RT-P13 — refund() blocked for already-REFUNDED payment
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-P13 | refund() throws BadRequestException for an already-REFUNDED payment")
    void rtP13_refund_alreadyRefundedPayment_throwsBadRequestException() {
        Payment refundedPayment = Payment.builder()
                .id(3L).booking(completedBooking)
                .amount(new BigDecimal("3500.00"))
                .status(PaymentStatus.REFUNDED)
                .stripePaymentIntent("pi_refunded_xyz")
                .build();

        when(paymentRepository.findById(3L)).thenReturn(Optional.of(refundedPayment));

        assertThatThrownBy(() -> paymentService.refund(3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only completed payments can be refunded");
    }

    // ===================================================================
    // RT-P14 — refund() on unknown paymentId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-P14 | refund() throws ResourceNotFoundException for unknown paymentId")
    void rtP14_refund_unknownPaymentId_throwsResourceNotFoundException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refund(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found");
    }

    // ===================================================================
    // RT-P15 — getByBookingId() returns payment when found
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-P15 | getByBookingId() returns the correct payment for a known bookingId")
    void rtP15_getByBookingId_returnsPayment() {
        when(paymentRepository.findByBookingId(50L)).thenReturn(Optional.of(pendingPayment));

        Payment result = paymentService.getByBookingId(50L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(paymentRepository).findByBookingId(50L);
    }

    // ===================================================================
    // RT-P16 — getByBookingId() throws ResourceNotFoundException when not found
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-P16 | getByBookingId() throws ResourceNotFoundException for unknown bookingId")
    void rtP16_getByBookingId_unknownBookingId_throwsResourceNotFoundException() {
        when(paymentRepository.findByBookingId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByBookingId(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No payment for booking");
    }

    // ===================================================================
    // RT-P17 — Payment default method is "STRIPE"
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-P17 | Payment entity default method is 'STRIPE'")
    void rtP17_payment_defaultMethodIsStripe() {
        Payment payment = Payment.builder()
                .booking(completedBooking)
                .amount(new BigDecimal("3500.00"))
                .build();

        assertThat(payment.getMethod())
                .as("default payment method must be STRIPE")
                .isEqualTo("STRIPE");
    }

    // ===================================================================
    // RT-P18 — Payment default status is PENDING on creation
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-P18 | Payment entity default status is PENDING on creation")
    void rtP18_payment_defaultStatusIsPending() {
        Payment payment = Payment.builder()
                .booking(completedBooking)
                .amount(new BigDecimal("3500.00"))
                .build();

        assertThat(payment.getStatus())
                .as("default payment status must be PENDING")
                .isEqualTo(PaymentStatus.PENDING);
    }
}