package com.example.FixItNow.regression;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import org.mockito.ArgumentCaptor;
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

import com.example.FixItNow.entity.Booking;
import com.example.FixItNow.entity.Dispute;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BookingStatus;
import com.example.FixItNow.enums.DisputeStatus;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.BookingRepository;
import com.example.FixItNow.repository.DisputeRepository;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.service.DisputeService;
import com.example.FixItNow.service.NotificationService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Dispute Resolution (SRS §2.1.2)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that dispute raising, duplicate guards, resolution,
 *   escalation, admin notifications, and listing queries continue
 *   to work correctly after any code update.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.DisputeRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/DisputeRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-D01  raise() saves dispute with correct reason and OPEN status
 *   RT-D02  raise() links correct booking and raisedBy user
 *   RT-D03  raise() default status is OPEN
 *   RT-D04  raise() blocked when an open dispute already exists for the booking
 *   RT-D05  raise() blocked does NOT save duplicate dispute
 *   RT-D06  raise() throws ResourceNotFoundException for unknown bookingId
 *   RT-D07  raise() throws ResourceNotFoundException for unknown raisedById
 *   RT-D08  raise() sends DISPUTE_RAISED notification to all admins
 *   RT-D09  raise() sends DISPUTE_RAISED notification to each admin individually
 *   RT-D10  raise() returns the saved Dispute object (not null)
 *   RT-D11  resolve() sets status to RESOLVED
 *   RT-D12  resolve() stores the resolution text
 *   RT-D13  resolve() assigns the resolving admin
 *   RT-D14  resolve() stamps a non-null resolvedAt timestamp
 *   RT-D15  resolve() sends DISPUTE_RESOLVED notification to homeowner
 *   RT-D16  resolve() sends DISPUTE_RESOLVED notification to provider
 *   RT-D17  resolve() does NOT send provider notification when booking has no provider
 *   RT-D18  resolve() throws ResourceNotFoundException for unknown disputeId
 *   RT-D19  resolve() throws ResourceNotFoundException for unknown adminId
 *   RT-D20  escalate() transitions dispute status to ESCALATED
 *   RT-D21  escalate() saves the escalated dispute
 *   RT-D22  escalate() throws ResourceNotFoundException for unknown disputeId
 *   RT-D23  getByStatus() delegates to repository with correct status filter
 *   RT-D24  getAll() returns full dispute list from repository
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Dispute Regression — Dispute resolution still works after updates")
class DisputeRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private DisputeRepository   disputeRepository;
    @Mock private BookingRepository   bookingRepository;
    @Mock private UserRepository      userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private DisputeService disputeService;

    // ---------------------------------------------------------------- fixture
    private User    homeowner;
    private User    provider;
    private User    admin;
    private Booking completedBooking;
    private Dispute openDispute;

    @BeforeEach
    void setUp() {
        homeowner = User.builder()
                .id(1L).name("Alice").email("alice@fixitnow.test")
                .username("alice").passwordHash("hashed")
                .userType(UserType.HOMEOWNER).isActive(true)
                .build();

        provider = User.builder()
                .id(2L).name("Bob").email("bob@fixitnow.test")
                .username("bob").passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER).isVerified(true).isActive(true)
                .build();

        admin = User.builder()
                .id(3L).name("Carol Admin").email("carol@fixitnow.test")
                .username("carol").passwordHash("hashed")
                .userType(UserType.ADMIN).isActive(true)
                .build();

        completedBooking = Booking.builder()
                .id(50L).homeowner(homeowner).provider(provider)
                .status(BookingStatus.COMPLETED)
                .estimatedCost(new BigDecimal("3500.00"))
                .scheduledDate(LocalDateTime.now().minusDays(1))
                .build();

        openDispute = Dispute.builder()
                .id(10L).booking(completedBooking).raisedBy(homeowner)
                .reason("Work was not completed properly.")
                .status(DisputeStatus.OPEN)
                .build();
    }

    /** Stub the lookups needed for a successful raise(). */
    private void stubSuccessfulRaise() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(disputeRepository.existsByBookingIdAndStatus(50L, DisputeStatus.OPEN)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of(admin));
    }

    // ===================================================================
    // RT-D01 — raise() saves dispute with correct reason and OPEN status
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-D01 | raise() saves dispute with correct reason")
    void rtD01_raise_savesDisputeWithCorrectReason() {
        stubSuccessfulRaise();
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.raise(50L, 1L, "Work was not completed properly.");

        assertThat(captor.getValue().getReason())
                .isEqualTo("Work was not completed properly.");
    }

    // ===================================================================
    // RT-D02 — raise() links correct booking and raisedBy user
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-D02 | raise() links correct booking and raisedBy user on the saved dispute")
    void rtD02_raise_linksBookingAndRaisedBy() {
        stubSuccessfulRaise();
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.raise(50L, 1L, "Provider was rude.");

        assertThat(captor.getValue().getBooking()).isEqualTo(completedBooking);
        assertThat(captor.getValue().getRaisedBy()).isEqualTo(homeowner);
    }

    // ===================================================================
    // RT-D03 — raise() default status is OPEN
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-D03 | raise() sets initial dispute status to OPEN")
    void rtD03_raise_defaultStatusIsOpen() {
        stubSuccessfulRaise();
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.raise(50L, 1L, "Job incomplete.");

        assertThat(captor.getValue().getStatus()).isEqualTo(DisputeStatus.OPEN);
    }

    // ===================================================================
    // RT-D04 — raise() blocked when open dispute already exists
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-D04 | raise() throws BadRequestException when an OPEN dispute already exists for booking")
    void rtD04_raise_duplicateOpenDispute_throwsBadRequestException() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(disputeRepository.existsByBookingIdAndStatus(50L, DisputeStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> disputeService.raise(50L, 1L, "Duplicate dispute."))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("open dispute already exists");
    }

    // ===================================================================
    // RT-D05 — raise() does NOT save when duplicate detected
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-D05 | raise() never calls save() when a duplicate open dispute is detected")
    void rtD05_raise_duplicateDetected_neverCallsSave() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(disputeRepository.existsByBookingIdAndStatus(50L, DisputeStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> disputeService.raise(50L, 1L, "Again."))
                .isInstanceOf(BadRequestException.class);

        verify(disputeRepository, never()).save(any());
    }

    // ===================================================================
    // RT-D06 — raise() throws ResourceNotFoundException for unknown bookingId
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-D06 | raise() throws ResourceNotFoundException for unknown bookingId")
    void rtD06_raise_unknownBookingId_throwsResourceNotFoundException() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.raise(999L, 1L, "Reason."))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    // ===================================================================
    // RT-D07 — raise() throws ResourceNotFoundException for unknown raisedById
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-D07 | raise() throws ResourceNotFoundException for unknown raisedById")
    void rtD07_raise_unknownRaisedById_throwsResourceNotFoundException() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(disputeRepository.existsByBookingIdAndStatus(50L, DisputeStatus.OPEN)).thenReturn(false);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.raise(50L, 999L, "Reason."))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ===================================================================
    // RT-D08 — raise() sends DISPUTE_RAISED notification to all admins
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-D08 | raise() sends DISPUTE_RAISED notification to all admins")
    void rtD08_raise_sendsDisputeRaisedNotificationToAdmins() {
        stubSuccessfulRaise();
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.raise(50L, 1L, "Service not delivered.");

        verify(notificationService).send(eq(admin), eq("DISPUTE_RAISED"), anyString());
    }

    // ===================================================================
    // RT-D09 — raise() sends DISPUTE_RAISED to each admin individually
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-D09 | raise() sends DISPUTE_RAISED to each admin when multiple admins exist")
    void rtD09_raise_sendsNotificationToEachAdmin() {
        User admin2 = User.builder()
                .id(4L).name("Dave Admin").email("dave@fixitnow.test")
                .username("dave").passwordHash("hashed")
                .userType(UserType.ADMIN).isActive(true).build();

        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(disputeRepository.existsByBookingIdAndStatus(50L, DisputeStatus.OPEN)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(userRepository.findByUserType(UserType.ADMIN)).thenReturn(List.of(admin, admin2));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.raise(50L, 1L, "Bad service.");

        // Both admins must each receive a notification
        verify(notificationService).send(eq(admin),  eq("DISPUTE_RAISED"), anyString());
        verify(notificationService).send(eq(admin2), eq("DISPUTE_RAISED"), anyString());
    }

    // ===================================================================
    // RT-D10 — raise() returns saved Dispute (not null)
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-D10 | raise() returns the saved Dispute object with a non-null id")
    void rtD10_raise_returnsSavedDispute() {
        stubSuccessfulRaise();
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            d.setId(10L);
            return d;
        });

        Dispute result = disputeService.raise(50L, 1L, "Equipment damaged.");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    // ===================================================================
    // RT-D11 — resolve() sets status to RESOLVED
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-D11 | resolve() transitions dispute status to RESOLVED")
    void rtD11_resolve_setsStatusToResolved() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "Refund issued to homeowner.");

        assertThat(captor.getValue().getStatus()).isEqualTo(DisputeStatus.RESOLVED);
    }

    // ===================================================================
    // RT-D12 — resolve() stores resolution text
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-D12 | resolve() stores the resolution text on the dispute")
    void rtD12_resolve_storesResolutionText() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "Refund issued to homeowner.");

        assertThat(captor.getValue().getResolution())
                .isEqualTo("Refund issued to homeowner.");
    }

    // ===================================================================
    // RT-D13 — resolve() assigns the resolving admin
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-D13 | resolve() assigns the resolving admin on the dispute")
    void rtD13_resolve_assignsResolvingAdmin() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "Partial refund agreed.");

        assertThat(captor.getValue().getResolvedBy()).isEqualTo(admin);
    }

    // ===================================================================
    // RT-D14 — resolve() stamps a non-null resolvedAt timestamp
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-D14 | resolve() stamps a non-null resolvedAt timestamp")
    void rtD14_resolve_stampsResolvedAt() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "No fault found.");

        assertThat(captor.getValue().getResolvedAt())
                .as("resolvedAt must be set when dispute is resolved")
                .isNotNull();
    }

    // ===================================================================
    // RT-D15 — resolve() sends DISPUTE_RESOLVED to homeowner
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-D15 | resolve() sends DISPUTE_RESOLVED notification to the homeowner")
    void rtD15_resolve_sendsDisputeResolvedToHomeowner() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "Resolved in favour of homeowner.");

        verify(notificationService).send(eq(homeowner), eq("DISPUTE_RESOLVED"), anyString());
    }

    // ===================================================================
    // RT-D16 — resolve() sends DISPUTE_RESOLVED to provider
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-D16 | resolve() sends DISPUTE_RESOLVED notification to the provider")
    void rtD16_resolve_sendsDisputeResolvedToProvider() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "No further action needed.");

        verify(notificationService).send(eq(provider), eq("DISPUTE_RESOLVED"), anyString());
    }

    // ===================================================================
    // RT-D17 — resolve() does NOT notify provider when booking has no provider
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-D17 | resolve() does NOT send provider notification when booking has no provider")
    void rtD17_resolve_noProvider_doesNotSendProviderNotification() {
        completedBooking.setProvider(null);
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.resolve(10L, 3L, "Resolved.");

        // Only homeowner should receive the notification
        verify(notificationService, times(1)).send(any(), eq("DISPUTE_RESOLVED"), anyString());
        verify(notificationService).send(eq(homeowner), eq("DISPUTE_RESOLVED"), anyString());
    }

    // ===================================================================
    // RT-D18 — resolve() throws ResourceNotFoundException for unknown disputeId
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-D18 | resolve() throws ResourceNotFoundException for unknown disputeId")
    void rtD18_resolve_unknownDisputeId_throwsResourceNotFoundException() {
        when(disputeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.resolve(999L, 3L, "Resolution."))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Dispute not found");
    }

    // ===================================================================
    // RT-D19 — resolve() throws ResourceNotFoundException for unknown adminId
    // ===================================================================
    @Test
    @Order(19)
    @DisplayName("RT-D19 | resolve() throws ResourceNotFoundException for unknown adminId")
    void rtD19_resolve_unknownAdminId_throwsResourceNotFoundException() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.resolve(10L, 999L, "Resolution."))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Admin not found");
    }

    // ===================================================================
    // RT-D20 — escalate() transitions status to ESCALATED
    // ===================================================================
    @Test
    @Order(20)
    @DisplayName("RT-D20 | escalate() transitions dispute status to ESCALATED")
    void rtD20_escalate_setsStatusToEscalated() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        ArgumentCaptor<Dispute> captor = ArgumentCaptor.forClass(Dispute.class);
        when(disputeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.escalate(10L);

        assertThat(captor.getValue().getStatus()).isEqualTo(DisputeStatus.ESCALATED);
    }

    // ===================================================================
    // RT-D21 — escalate() saves the escalated dispute
    // ===================================================================
    @Test
    @Order(21)
    @DisplayName("RT-D21 | escalate() persists the escalated dispute to the repository")
    void rtD21_escalate_savesEscalatedDispute() {
        when(disputeRepository.findById(10L)).thenReturn(Optional.of(openDispute));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        disputeService.escalate(10L);

        verify(disputeRepository, times(1)).save(any(Dispute.class));
    }

    // ===================================================================
    // RT-D22 — escalate() throws ResourceNotFoundException for unknown disputeId
    // ===================================================================
    @Test
    @Order(22)
    @DisplayName("RT-D22 | escalate() throws ResourceNotFoundException for unknown disputeId")
    void rtD22_escalate_unknownDisputeId_throwsResourceNotFoundException() {
        when(disputeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.escalate(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Dispute not found");
    }

    // ===================================================================
    // RT-D23 — getByStatus() delegates to repository with correct filter
    // ===================================================================
    @Test
    @Order(23)
    @DisplayName("RT-D23 | getByStatus() delegates to repository with the correct DisputeStatus filter")
    void rtD23_getByStatus_delegatesWithCorrectFilter() {
        Dispute escalated = Dispute.builder()
                .id(11L).booking(completedBooking).raisedBy(homeowner)
                .reason("Escalated issue.").status(DisputeStatus.ESCALATED).build();

        when(disputeRepository.findByStatus(DisputeStatus.ESCALATED)).thenReturn(List.of(escalated));

        List<Dispute> result = disputeService.getByStatus(DisputeStatus.ESCALATED);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(DisputeStatus.ESCALATED);
        verify(disputeRepository).findByStatus(DisputeStatus.ESCALATED);
        verify(disputeRepository, never()).findAll();
    }

    // ===================================================================
    // RT-D24 — getAll() returns full dispute list from repository
    // ===================================================================
    @Test
    @Order(24)
    @DisplayName("RT-D24 | getAll() returns the full dispute list from the repository")
    void rtD24_getAll_returnsFullList() {
        Dispute resolved = Dispute.builder()
                .id(12L).booking(completedBooking).raisedBy(homeowner)
                .reason("Already resolved.").status(DisputeStatus.RESOLVED).build();

        when(disputeRepository.findAll()).thenReturn(List.of(openDispute, resolved));

        List<Dispute> result = disputeService.getAll();

        assertThat(result).hasSize(2);
        verify(disputeRepository).findAll();
    }
}