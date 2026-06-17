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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.FixItNow.dto.request.BookingRequest;
import com.example.FixItNow.entity.Booking;
import com.example.FixItNow.entity.Service;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BookingStatus;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.BookingRepository;
import com.example.FixItNow.repository.ServiceRepository;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.service.BookingService;
import com.example.FixItNow.service.NotificationService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Booking Lifecycle (SRS FR8)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that the full booking lifecycle (create, accept, start,
 *   complete, cancel) continues to work correctly after any code update.
 *   Covers state transitions, notification triggers, blacklist checks,
 *   and invalid transition guards.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.BookingRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/BookingRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-B01  createBooking() sets status to PENDING
 *   RT-B02  createBooking() stores estimatedCost from service.dayPayment
 *   RT-B03  createBooking() links correct homeowner and service
 *   RT-B04  createBooking() sends JOB_REQUEST notification to provider
 *   RT-B05  createBooking() does NOT send notification when no provider assigned
 *   RT-B06  Blacklisted homeowner cannot create a booking
 *   RT-B07  Unverified provider cannot be assigned to a booking
 *   RT-B08  Unknown homeownerId throws ResourceNotFoundException
 *   RT-B09  Unknown serviceId throws ResourceNotFoundException
 *   RT-B10  acceptBooking() transitions PENDING → ACCEPTED
 *   RT-B11  acceptBooking() assigns provider to booking
 *   RT-B12  acceptBooking() sends BOOKING_ACCEPTED notification to homeowner
 *   RT-B13  acceptBooking() on non-PENDING booking throws BadRequestException
 *   RT-B14  startJob() transitions ACCEPTED → IN_PROGRESS
 *   RT-B15  startJob() sends JOB_STARTED notification to homeowner
 *   RT-B16  startJob() on non-ACCEPTED booking throws BadRequestException
 *   RT-B17  completeJob() transitions IN_PROGRESS → COMPLETED
 *   RT-B18  completeJob() sends JOB_COMPLETED notification to homeowner
 *   RT-B19  completeJob() on non-IN_PROGRESS booking throws BadRequestException
 *   RT-B20  cancelBooking() transitions PENDING → CANCELLED with reason
 *   RT-B21  cancelBooking() transitions ACCEPTED → CANCELLED with reason
 *   RT-B22  cancelBooking() on COMPLETED booking throws BadRequestException
 *   RT-B23  getByHomeowner() delegates to repository correctly
 *   RT-B24  getByProvider() delegates to repository correctly
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Booking Regression — Booking lifecycle still works after updates")
class BookingRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private BookingRepository     bookingRepository;
    @Mock private UserRepository        userRepository;
    @Mock private ServiceRepository     serviceRepository;
    @Mock private NotificationService   notificationService;

    @InjectMocks private BookingService bookingService;

    // ---------------------------------------------------------------- fixture
    private User     homeowner;
    private User     provider;
    private Service  service;
    private BookingRequest bookingRequest;

    @BeforeEach
    void setUp() {
        homeowner = User.builder()
                .id(1L).name("Alice").email("alice@fixitnow.test")
                .username("alice").passwordHash("hashed")
                .userType(UserType.HOMEOWNER).isActive(true).isBlacklisted(false)
                .build();

        provider = User.builder()
                .id(2L).name("Bob").email("bob@fixitnow.test")
                .username("bob").passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER).isVerified(true).isActive(true)
                .build();

        service = Service.builder()
                .id(10L).name("Pipe Leak Repair")
                .dayPayment(new BigDecimal("3500.00")).isActive(true)
                .build();

        bookingRequest = new BookingRequest();
        bookingRequest.setServiceId(10L);
        bookingRequest.setProviderId(2L);
        bookingRequest.setScheduledDate(LocalDateTime.now().plusDays(2));
        bookingRequest.setDescription("Kitchen pipe is leaking badly.");
        bookingRequest.setServiceType("Plumbing");
    }

    /** Helper: build a booking at a given status. */
    private Booking buildBooking(Long id, BookingStatus status) {
        return Booking.builder()
                .id(id).homeowner(homeowner).provider(provider)
                .service(service).status(status)
                .estimatedCost(new BigDecimal("3500.00"))
                .scheduledDate(LocalDateTime.now().plusDays(2))
                .build();
    }

    /** Stub the three lookups createBooking() always does. */
    private void stubCreateLookups() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
    }

    // ===================================================================
    // RT-B01 — createBooking() sets initial status to PENDING
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-B01 | createBooking() sets booking status to PENDING")
    void rtB01_createBooking_statusIsPending() {
        stubCreateLookups();
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(buildBooking(1L, BookingStatus.PENDING));

        bookingService.createBooking(1L, bookingRequest);

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ===================================================================
    // RT-B02 — createBooking() stores estimatedCost from service.dayPayment
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-B02 | createBooking() sets estimatedCost from service day rate")
    void rtB02_createBooking_estimatedCostFromServiceDayRate() {
        stubCreateLookups();
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(buildBooking(1L, BookingStatus.PENDING));

        bookingService.createBooking(1L, bookingRequest);

        assertThat(captor.getValue().getEstimatedCost())
                .as("estimatedCost must match the service day rate")
                .isEqualByComparingTo(new BigDecimal("3500.00"));
    }

    // ===================================================================
    // RT-B03 — createBooking() links correct homeowner and service
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-B03 | createBooking() links correct homeowner and service on the booking")
    void rtB03_createBooking_linksHomeownerAndService() {
        stubCreateLookups();
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(buildBooking(1L, BookingStatus.PENDING));

        bookingService.createBooking(1L, bookingRequest);

        assertThat(captor.getValue().getHomeowner()).isEqualTo(homeowner);
        assertThat(captor.getValue().getService()).isEqualTo(service);
    }

    // ===================================================================
    // RT-B04 — createBooking() sends JOB_REQUEST notification to provider
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-B04 | createBooking() sends JOB_REQUEST notification to the assigned provider")
    void rtB04_createBooking_sendsJobRequestNotificationToProvider() {
        stubCreateLookups();
        when(bookingRepository.save(any())).thenReturn(buildBooking(1L, BookingStatus.PENDING));

        bookingService.createBooking(1L, bookingRequest);

        verify(notificationService).send(eq(provider), eq("JOB_REQUEST"), anyString());
    }

    // ===================================================================
    // RT-B05 — createBooking() does NOT notify when no provider assigned
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-B05 | createBooking() does NOT send notification when no provider is assigned")
    void rtB05_createBooking_noNotificationWhenNoProvider() {
        bookingRequest.setProviderId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(bookingRepository.save(any())).thenReturn(buildBooking(1L, BookingStatus.PENDING));

        bookingService.createBooking(1L, bookingRequest);

        verify(notificationService, never()).send(any(), anyString(), anyString());
    }

    // ===================================================================
    // RT-B06 — Blacklisted homeowner cannot create a booking
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-B06 | Blacklisted homeowner is blocked from creating a booking")
    void rtB06_createBooking_blacklistedHomeowner_throwsBadRequestException() {
        homeowner.setBlacklisted(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));

        assertThatThrownBy(() -> bookingService.createBooking(1L, bookingRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("suspended");

        verify(bookingRepository, never()).save(any());
    }

    // ===================================================================
    // RT-B07 — Unverified provider cannot be assigned
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-B07 | Unverified provider cannot be assigned to a booking")
    void rtB07_createBooking_unverifiedProvider_throwsBadRequestException() {
        provider.setVerified(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> bookingService.createBooking(1L, bookingRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not yet verified");
    }

    // ===================================================================
    // RT-B08 — Unknown homeownerId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-B08 | Unknown homeownerId throws ResourceNotFoundException")
    void rtB08_createBooking_unknownHomeowner_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(999L, bookingRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Homeowner not found");
    }

    // ===================================================================
    // RT-B09 — Unknown serviceId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-B09 | Unknown serviceId throws ResourceNotFoundException")
    void rtB09_createBooking_unknownService_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());
        bookingRequest.setServiceId(99L);

        assertThatThrownBy(() -> bookingService.createBooking(1L, bookingRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Service not found");
    }

    // ===================================================================
    // RT-B10 — acceptBooking() transitions PENDING → ACCEPTED
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-B10 | acceptBooking() transitions booking from PENDING to ACCEPTED")
    void rtB10_acceptBooking_pendingToAccepted() {
        Booking pending = buildBooking(1L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(pending);

        bookingService.acceptBooking(1L, 2L);

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    // ===================================================================
    // RT-B11 — acceptBooking() assigns provider to booking
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-B11 | acceptBooking() assigns the provider to the booking entity")
    void rtB11_acceptBooking_assignsProvider() {
        Booking pending = buildBooking(1L, BookingStatus.PENDING);
        pending.setProvider(null); // no provider initially
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(pending);

        bookingService.acceptBooking(1L, 2L);

        assertThat(captor.getValue().getProvider()).isEqualTo(provider);
    }

    // ===================================================================
    // RT-B12 — acceptBooking() sends BOOKING_ACCEPTED notification
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-B12 | acceptBooking() sends BOOKING_ACCEPTED notification to homeowner")
    void rtB12_acceptBooking_sendsBookingAcceptedNotification() {
        Booking pending = buildBooking(1L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(bookingRepository.save(any())).thenReturn(pending);

        bookingService.acceptBooking(1L, 2L);

        verify(notificationService).send(eq(homeowner), eq("BOOKING_ACCEPTED"), anyString());
    }

    // ===================================================================
    // RT-B13 — acceptBooking() on non-PENDING throws BadRequestException
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-B13 | acceptBooking() on a non-PENDING booking throws BadRequestException")
    void rtB13_acceptBooking_nonPendingStatus_throwsBadRequestException() {
        Booking accepted = buildBooking(1L, BookingStatus.ACCEPTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> bookingService.acceptBooking(1L, 2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PENDING");
    }

    // ===================================================================
    // RT-B14 — startJob() transitions ACCEPTED → IN_PROGRESS
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-B14 | startJob() transitions booking from ACCEPTED to IN_PROGRESS")
    void rtB14_startJob_acceptedToInProgress() {
        Booking accepted = buildBooking(1L, BookingStatus.ACCEPTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(accepted));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(accepted);

        bookingService.startJob(1L);

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
    }

    // ===================================================================
    // RT-B15 — startJob() sends JOB_STARTED notification
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-B15 | startJob() sends JOB_STARTED notification to homeowner")
    void rtB15_startJob_sendsJobStartedNotification() {
        Booking accepted = buildBooking(1L, BookingStatus.ACCEPTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(accepted));
        when(bookingRepository.save(any())).thenReturn(accepted);

        bookingService.startJob(1L);

        verify(notificationService).send(eq(homeowner), eq("JOB_STARTED"), anyString());
    }

    // ===================================================================
    // RT-B16 — startJob() on non-ACCEPTED throws BadRequestException
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-B16 | startJob() on a non-ACCEPTED booking throws BadRequestException")
    void rtB16_startJob_nonAcceptedStatus_throwsBadRequestException() {
        Booking pending = buildBooking(1L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> bookingService.startJob(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACCEPTED");
    }

    // ===================================================================
    // RT-B17 — completeJob() transitions IN_PROGRESS → COMPLETED
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-B17 | completeJob() transitions booking from IN_PROGRESS to COMPLETED")
    void rtB17_completeJob_inProgressToCompleted() {
        Booking inProgress = buildBooking(1L, BookingStatus.IN_PROGRESS);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(inProgress);

        bookingService.completeJob(1L);

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.COMPLETED);
    }

    // ===================================================================
    // RT-B18 — completeJob() sends JOB_COMPLETED notification
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-B18 | completeJob() sends JOB_COMPLETED notification to homeowner")
    void rtB18_completeJob_sendsJobCompletedNotification() {
        Booking inProgress = buildBooking(1L, BookingStatus.IN_PROGRESS);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(inProgress));
        when(bookingRepository.save(any())).thenReturn(inProgress);

        bookingService.completeJob(1L);

        verify(notificationService).send(eq(homeowner), eq("JOB_COMPLETED"), anyString());
    }

    // ===================================================================
    // RT-B19 — completeJob() on non-IN_PROGRESS throws BadRequestException
    // ===================================================================
    @Test
    @Order(19)
    @DisplayName("RT-B19 | completeJob() on a non-IN_PROGRESS booking throws BadRequestException")
    void rtB19_completeJob_nonInProgressStatus_throwsBadRequestException() {
        Booking accepted = buildBooking(1L, BookingStatus.ACCEPTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> bookingService.completeJob(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("IN_PROGRESS");
    }

    // ===================================================================
    // RT-B20 — cancelBooking() transitions PENDING → CANCELLED with reason
    // ===================================================================
    @Test
    @Order(20)
    @DisplayName("RT-B20 | cancelBooking() transitions PENDING booking to CANCELLED and stores reason")
    void rtB20_cancelBooking_pendingToCancelled() {
        Booking pending = buildBooking(1L, BookingStatus.PENDING);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(pending));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(pending);

        bookingService.cancelBooking(1L, "Provider unavailable.");

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(captor.getValue().getCancellationReason()).isEqualTo("Provider unavailable.");
    }

    // ===================================================================
    // RT-B21 — cancelBooking() also works on ACCEPTED bookings
    // ===================================================================
    @Test
    @Order(21)
    @DisplayName("RT-B21 | cancelBooking() transitions ACCEPTED booking to CANCELLED")
    void rtB21_cancelBooking_acceptedToCancelled() {
        Booking accepted = buildBooking(1L, BookingStatus.ACCEPTED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(accepted));
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        when(bookingRepository.save(captor.capture())).thenReturn(accepted);

        bookingService.cancelBooking(1L, "Changed my mind.");

        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    // ===================================================================
    // RT-B22 — cancelBooking() on COMPLETED booking throws BadRequestException
    // ===================================================================
    @Test
    @Order(22)
    @DisplayName("RT-B22 | cancelBooking() on a COMPLETED booking throws BadRequestException")
    void rtB22_cancelBooking_completedBooking_throwsBadRequestException() {
        Booking completed = buildBooking(1L, BookingStatus.COMPLETED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, "Too late."))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot cancel");
    }

    // ===================================================================
    // RT-B23 — getByHomeowner() delegates to repository
    // ===================================================================
    @Test
    @Order(23)
    @DisplayName("RT-B23 | getByHomeowner() returns all bookings for the given homeowner")
    void rtB23_getByHomeowner_returnsCorrectList() {
        List<Booking> expected = List.of(
                buildBooking(1L, BookingStatus.PENDING),
                buildBooking(2L, BookingStatus.COMPLETED)
        );
        when(bookingRepository.findByHomeownerId(1L)).thenReturn(expected);

        List<Booking> result = bookingService.getByHomeowner(1L);

        assertThat(result).hasSize(2);
        verify(bookingRepository).findByHomeownerId(1L);
    }

    // ===================================================================
    // RT-B24 — getByProvider() delegates to repository
    // ===================================================================
    @Test
    @Order(24)
    @DisplayName("RT-B24 | getByProvider() returns all bookings for the given provider")
    void rtB24_getByProvider_returnsCorrectList() {
        List<Booking> expected = List.of(buildBooking(3L, BookingStatus.ACCEPTED));
        when(bookingRepository.findByProviderId(2L)).thenReturn(expected);

        List<Booking> result = bookingService.getByProvider(2L);

        assertThat(result).hasSize(1);
        verify(bookingRepository).findByProviderId(2L);
    }
}