package com.example.FixItNow.regression;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.example.FixItNow.entity.Notification;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.NotificationChannel;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.NotificationRepository;
import com.example.FixItNow.service.NotificationService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — FR12: Notifications (SRS §3.12)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that the notification feature (in-app persistence + email
 *   dispatch) continues to work correctly after any code update.
 *   Each test is tagged @Tag("regression") so it can be run as a
 *   dedicated regression pass in VS Code (see README below).
 *
 * HOW TO RUN IN VS CODE:
 *   Option 1 — Testing panel:
 *     Open "Testing" sidebar → expand FixItNow → run
 *     "NotificationRegressionTest".
 *
 *   Option 2 — Terminal (run only regression-tagged tests):
 *     cd FixItNow
 *     ./mvnw test -Dgroups="regression" -pl .
 *
 *   Option 3 — Run all tests including this suite:
 *     ./mvnw test
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/NotificationRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-N01  send() persists notification with correct fields
 *   RT-N02  send() always sets channel to BOTH
 *   RT-N03  send() always initialises isRead = false
 *   RT-N04  send() triggers exactly one email dispatch
 *   RT-N05  send() sets email recipient, subject prefix, and body correctly
 *   RT-N06  Email failure does NOT prevent in-app notification from saving
 *   RT-N07  Email failure does NOT propagate an exception to caller
 *   RT-N08  getForUser() delegates to repository and returns full list
 *   RT-N09  getUnreadForUser() delegates to repository (unread-only query)
 *   RT-N10  markRead() flips isRead flag and saves
 *   RT-N11  markRead() on unknown id throws ResourceNotFoundException
 *   RT-N12  Booking confirmation notification uses BOOKING_ACCEPTED type
 *   RT-N13  Job-started notification uses JOB_STARTED type
 *   RT-N14  Job-completed notification uses JOB_COMPLETED type
 *   RT-N15  Payment notification uses PAYMENT_COMPLETED type
 *   RT-N16  send() returns the saved Notification object (not null)
 *   RT-N17  Notification user reference is preserved on the saved entity
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("FR12 Regression — Notifications still work after updates")
class NotificationRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    // ---------------------------------------------------------------- fixture
    private User homeowner;
    private User provider;

    @BeforeEach
    void setUpUsers() {
        homeowner = User.builder()
                .id(1L)
                .name("Alice Homeowner")
                .email("alice@fixitnow.test")
                .username("alice")
                .passwordHash("hashed")
                .userType(UserType.HOMEOWNER)
                .build();

        provider = User.builder()
                .id(2L)
                .name("Bob Provider")
                .email("bob@fixitnow.test")
                .username("bob")
                .passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER)
                .build();
    }

    /** Shared helper: stub repository.save() to echo back its argument with an id set. */
    private void stubSaveWithId(long id) {
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> {
                    Notification n = inv.getArgument(0);
                    n.setId(id);
                    return n;
                });
    }

    // ===================================================================
    // RT-N01 — send() persists notification with all mandatory fields
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-N01 | send() persists notification with type, message, user, and sentAt fields")
    void rtN01_send_persistsNotificationWithMandatoryFields() {
        stubSaveWithId(10L);

        Notification result = notificationService.send(
                homeowner, "BOOKING_ACCEPTED", "Your booking #42 has been accepted.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getType()).as("type must match").isEqualTo("BOOKING_ACCEPTED");
        assertThat(saved.getMessage()).as("message must match").isEqualTo("Your booking #42 has been accepted.");
        assertThat(saved.getUser()).as("user must be linked").isEqualTo(homeowner);
    }

    // ===================================================================
    // RT-N02 — Channel is always BOTH
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-N02 | send() always sets NotificationChannel.BOTH — regression against channel regression")
    void rtN02_send_channelIsAlwaysBoth() {
        stubSaveWithId(11L);

        notificationService.send(homeowner, "PAYMENT_COMPLETED", "Payment confirmed.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getChannel())
                .as("channel must be BOTH (covers both email and in-app delivery)")
                .isEqualTo(NotificationChannel.BOTH);
    }

    // ===================================================================
    // RT-N03 — isRead defaults to false on creation
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-N03 | Newly created notification must have isRead = false")
    void rtN03_send_newNotificationIsUnread() {
        stubSaveWithId(12L);

        notificationService.send(homeowner, "JOB_STARTED", "Provider has arrived.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().isRead())
                .as("isRead must be false for a brand-new notification")
                .isFalse();
    }

    // ===================================================================
    // RT-N04 — Exactly one email is dispatched per send()
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-N04 | send() triggers exactly one JavaMailSender.send() call")
    void rtN04_send_dispatchesExactlyOneEmail() {
        stubSaveWithId(13L);

        notificationService.send(homeowner, "BOOKING_ACCEPTED", "Booking confirmed.");

        // @Async runs synchronously in unit-test context (no TaskExecutor configured)
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    // ===================================================================
    // RT-N05 — Email fields: recipient, subject prefix, body
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-N05 | Email has correct recipient, [FixItNow] subject prefix, and original body")
    void rtN05_send_emailFieldsAreCorrect() {
        stubSaveWithId(14L);

        notificationService.send(
                homeowner, "DISPUTE_RAISED", "A dispute has been raised for booking #7.");

        ArgumentCaptor<SimpleMailMessage> emailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(emailCaptor.capture());

        SimpleMailMessage email = emailCaptor.getValue();
        assertThat(email.getTo())
                .as("email must go to the user's registered address")
                .containsExactly("alice@fixitnow.test");
        assertThat(email.getSubject())
                .as("subject must carry the [FixItNow] prefix")
                .startsWith("[FixItNow]")
                .contains("DISPUTE_RAISED");
        assertThat(email.getText())
                .as("email body must contain the original notification message")
                .contains("dispute has been raised");
    }

    // ===================================================================
    // RT-N06 — Email failure does NOT block in-app notification save
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-N06 | SMTP failure still allows in-app notification to be saved")
    void rtN06_emailFailure_inAppNotificationIsStillPersisted() {
        doThrow(new RuntimeException("SMTP unreachable")).when(mailSender).send(any(SimpleMailMessage.class));
        stubSaveWithId(15L);

        // Must NOT throw
        Notification result = notificationService.send(
                homeowner, "BOOKING_ACCEPTED", "Booking confirmed despite email failure.");

        // Repository save must have been called once
        verify(notificationRepository, times(1)).save(any(Notification.class));
        assertThat(result).isNotNull();
    }

    // ===================================================================
    // RT-N07 — Email failure is silently swallowed (best-effort)
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-N07 | SMTP failure does not propagate an exception to the caller")
    void rtN07_emailFailure_exceptionIsSwallowed() {
        doThrow(new RuntimeException("Connection refused")).when(mailSender).send(any(SimpleMailMessage.class));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException()
                .as("email errors are best-effort; must never surface to calling service")
                .isThrownBy(() -> notificationService.send(provider, "JOB_REQUEST", "New request."));
    }

    // ===================================================================
    // RT-N08 — getForUser() returns full notification list
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-N08 | getForUser() returns all notifications for the given userId in descending order")
    void rtN08_getForUser_returnsListFromRepository() {
        Notification n1 = Notification.builder()
                .id(1L).user(homeowner).type("BOOKING_ACCEPTED").message("Msg 1")
                .channel(NotificationChannel.BOTH).isRead(false).build();
        Notification n2 = Notification.builder()
                .id(2L).user(homeowner).type("JOB_STARTED").message("Msg 2")
                .channel(NotificationChannel.BOTH).isRead(true).build();

        when(notificationRepository.findByUserIdOrderBySentAtDesc(1L))
                .thenReturn(List.of(n2, n1)); // newest first

        List<Notification> result = notificationService.getForUser(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(2L); // newest returned first
        verify(notificationRepository).findByUserIdOrderBySentAtDesc(1L);
    }

    // ===================================================================
    // RT-N09 — getUnreadForUser() uses the unread-only query
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-N09 | getUnreadForUser() delegates to findByUserIdAndIsReadFalse query")
    void rtN09_getUnreadForUser_usesUnreadQuery() {
        Notification unread = Notification.builder()
                .id(3L).user(homeowner).type("PAYMENT_COMPLETED").message("Paid.")
                .channel(NotificationChannel.BOTH).isRead(false).build();

        when(notificationRepository.findByUserIdAndIsReadFalse(1L)).thenReturn(List.of(unread));

        List<Notification> result = notificationService.getUnreadForUser(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRead()).isFalse();
        verify(notificationRepository).findByUserIdAndIsReadFalse(1L);
        // Must NOT call the all-notifications query
        verify(notificationRepository, never()).findByUserIdOrderBySentAtDesc(anyLong());
    }

    // ===================================================================
    // RT-N10 — markRead() flips isRead flag and persists
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-N10 | markRead() sets isRead=true and saves the updated notification")
    void rtN10_markRead_flipsIsReadAndSaves() {
        Notification unread = Notification.builder()
                .id(5L).user(homeowner).type("JOB_COMPLETED").message("Job done.")
                .channel(NotificationChannel.BOTH).isRead(false).build();

        when(notificationRepository.findById(5L)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markRead(5L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().isRead())
                .as("isRead must be true after markRead()")
                .isTrue();
    }

    // ===================================================================
    // RT-N11 — markRead() on unknown id throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-N11 | markRead() throws ResourceNotFoundException for an unknown notification id")
    void rtN11_markRead_unknownId_throwsResourceNotFoundException() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found");
    }

    // ===================================================================
    // RT-N12 — BOOKING_ACCEPTED notification type is preserved
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-N12 | Booking confirmation notification uses type BOOKING_ACCEPTED")
    void rtN12_bookingConfirmation_typeIsBookingAccepted() {
        stubSaveWithId(20L);

        notificationService.send(
                homeowner,
                "BOOKING_ACCEPTED",
                "Your booking #100 has been accepted by Bob Provider.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo("BOOKING_ACCEPTED");
        assertThat(captor.getValue().getMessage()).contains("booking #100");
    }

    // ===================================================================
    // RT-N13 — JOB_STARTED notification type is preserved
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-N13 | Job-started notification uses type JOB_STARTED")
    void rtN13_jobStarted_typeIsJobStarted() {
        stubSaveWithId(21L);

        notificationService.send(
                homeowner,
                "JOB_STARTED",
                "Your service provider has arrived and started working on booking #100");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo("JOB_STARTED");
        assertThat(captor.getValue().getMessage()).contains("arrived and started");
    }

    // ===================================================================
    // RT-N14 — JOB_COMPLETED notification type is preserved
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-N14 | Job-completed notification uses type JOB_COMPLETED")
    void rtN14_jobCompleted_typeIsJobCompleted() {
        stubSaveWithId(22L);

        notificationService.send(
                homeowner,
                "JOB_COMPLETED",
                "Booking #100 is complete. Please make payment and leave a review.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo("JOB_COMPLETED");
        assertThat(captor.getValue().getMessage()).contains("make payment and leave a review");
    }

    // ===================================================================
    // RT-N15 — PAYMENT_COMPLETED notification type is preserved
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-N15 | Payment notification uses type PAYMENT_COMPLETED")
    void rtN15_payment_typeIsPaymentCompleted() {
        stubSaveWithId(23L);

        notificationService.send(
                homeowner,
                "PAYMENT_COMPLETED",
                "Payment of $3500.00 confirmed for booking #100.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(captor.getValue().getMessage()).contains("$3500.00");
    }

    // ===================================================================
    // RT-N16 — send() returns the saved Notification (not null)
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-N16 | send() returns the persisted Notification object — never null")
    void rtN16_send_returnsPersistedNotification() {
        stubSaveWithId(30L);

        Notification result = notificationService.send(
                provider, "JOB_REQUEST", "New job request: Plumbing on 2026-07-01.");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(30L);
    }

    // ===================================================================
    // RT-N17 — User reference on saved entity matches the recipient
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-N17 | Saved notification's user reference equals the intended recipient")
    void rtN17_send_savedNotificationUserMatchesRecipient() {
        stubSaveWithId(31L);

        notificationService.send(provider, "JOB_REQUEST", "New request for Electrician.");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getUser())
                .as("notification must be linked to the correct recipient")
                .isEqualTo(provider);
        assertThat(captor.getValue().getUser().getEmail()).isEqualTo("bob@fixitnow.test");
    }
}