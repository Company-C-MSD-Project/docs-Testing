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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.FixItNow.entity.Booking;
import com.example.FixItNow.entity.Review;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BadgeLevel;
import com.example.FixItNow.enums.BookingStatus;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.BookingRepository;
import com.example.FixItNow.repository.ReviewRepository;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.service.ReviewService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Reviews & Reputation (SRS FR11)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that review submission, duplicate guards, ownership
 *   checks, badge recalculation, and provider rating updates
 *   continue to work correctly after any code update.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.ReviewRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/ReviewRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-R01  submitReview() saves review with correct rating and comment
 *   RT-R02  submitReview() links correct homeowner and provider on the review
 *   RT-R03  submitReview() links the correct booking on the review
 *   RT-R04  submitReview() blocked for non-COMPLETED booking
 *   RT-R05  submitReview() blocked when review already exists for booking
 *   RT-R06  submitReview() blocked when homeowner doesn't own the booking
 *   RT-R07  submitReview() on unknown bookingId throws ResourceNotFoundException
 *   RT-R08  submitReview() triggers badge recalculation for provider
 *   RT-R09  recalculateBadge() sets rating as rounded average from repository
 *   RT-R10  recalculateBadge() assigns NONE badge when below all thresholds
 *   RT-R11  recalculateBadge() assigns BRONZE badge (avg >= 3.0, jobs >= 5)
 *   RT-R12  recalculateBadge() assigns SILVER badge (avg >= 3.5, jobs >= 20)
 *   RT-R13  recalculateBadge() assigns GOLD badge (avg >= 4.0, jobs >= 50)
 *   RT-R14  recalculateBadge() assigns TOP_RATED badge (avg >= 4.5, jobs >= 100)
 *   RT-R15  recalculateBadge() on unknown providerId throws ResourceNotFoundException
 *   RT-R16  recalculateBadge() defaults rating to 0.0 when no reviews exist
 *   RT-R17  getProviderReviews() delegates to repository and returns full list
 *   RT-R18  Review default isFlagged is false on creation
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Review Regression — Reviews and reputation still work after updates")
class ReviewRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private ReviewRepository  reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository    userRepository;

    @InjectMocks private ReviewService reviewService;

    // ---------------------------------------------------------------- fixture
    private User    homeowner;
    private User    provider;
    private Booking completedBooking;
    private Booking pendingBooking;

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
    }

    /** Helper: stub all lookups needed for a successful submitReview(). */
    private void stubSuccessfulSubmit() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(50L)).thenReturn(false);
        // badge recalculation stubs
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(4.2));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(55L);
        when(userRepository.save(any())).thenReturn(provider);
    }

    // ===================================================================
    // RT-R01 — submitReview() saves review with correct rating and comment
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-R01 | submitReview() saves review with correct rating and comment")
    void rtR01_submitReview_savesCorrectRatingAndComment() {
        stubSuccessfulSubmit();
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        when(reviewRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(1L, 50L, 5, "Excellent work, very professional!");

        assertThat(captor.getValue().getRating()).isEqualTo(5);
        assertThat(captor.getValue().getComment()).isEqualTo("Excellent work, very professional!");
    }

    // ===================================================================
    // RT-R02 — submitReview() links correct homeowner and provider
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-R02 | submitReview() links correct homeowner and provider on the saved review")
    void rtR02_submitReview_linksCorrectHomeownerAndProvider() {
        stubSuccessfulSubmit();
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        when(reviewRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(1L, 50L, 4, "Good service.");

        assertThat(captor.getValue().getHomeowner()).isEqualTo(homeowner);
        assertThat(captor.getValue().getProvider()).isEqualTo(provider);
    }

    // ===================================================================
    // RT-R03 — submitReview() links the correct booking
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-R03 | submitReview() links the correct booking on the saved review")
    void rtR03_submitReview_linksCorrectBooking() {
        stubSuccessfulSubmit();
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        when(reviewRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(1L, 50L, 3, "Average experience.");

        assertThat(captor.getValue().getBooking()).isEqualTo(completedBooking);
        assertThat(captor.getValue().getBooking().getId()).isEqualTo(50L);
    }

    // ===================================================================
    // RT-R04 — submitReview() blocked for non-COMPLETED booking
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-R04 | submitReview() throws BadRequestException for a non-COMPLETED booking")
    void rtR04_submitReview_nonCompletedBooking_throwsBadRequestException() {
        when(bookingRepository.findById(51L)).thenReturn(Optional.of(pendingBooking));

        assertThatThrownBy(() -> reviewService.submitReview(1L, 51L, 5, "Great!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed bookings");

        verify(reviewRepository, never()).save(any());
    }

    // ===================================================================
    // RT-R05 — submitReview() blocked when review already exists
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-R05 | submitReview() throws BadRequestException when review already submitted for booking")
    void rtR05_submitReview_duplicateReview_throwsBadRequestException() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(50L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.submitReview(1L, 50L, 5, "Again!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been submitted");

        verify(reviewRepository, never()).save(any());
    }

    // ===================================================================
    // RT-R06 — submitReview() blocked when homeowner doesn't own booking
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-R06 | submitReview() throws BadRequestException when homeowner doesn't own booking")
    void rtR06_submitReview_wrongHomeowner_throwsBadRequestException() {
        when(bookingRepository.findById(50L)).thenReturn(Optional.of(completedBooking));
        when(reviewRepository.existsByBookingId(50L)).thenReturn(false);

        // homeownerId 99L does not own booking (which belongs to homeowner id=1L)
        assertThatThrownBy(() -> reviewService.submitReview(99L, 50L, 5, "Sneaky review!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only review bookings you made");

        verify(reviewRepository, never()).save(any());
    }

    // ===================================================================
    // RT-R07 — submitReview() on unknown bookingId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-R07 | submitReview() throws ResourceNotFoundException for unknown bookingId")
    void rtR07_submitReview_unknownBookingId_throwsResourceNotFoundException() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.submitReview(1L, 999L, 4, "Great!"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Booking not found");
    }

    // ===================================================================
    // RT-R08 — submitReview() triggers badge recalculation for provider
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-R08 | submitReview() triggers badge recalculation for the provider")
    void rtR08_submitReview_triggersBadgeRecalculation() {
        stubSuccessfulSubmit();
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(1L, 50L, 5, "Amazing!");

        // Badge recalculation must save updated provider
        verify(userRepository).save(eq(provider));
    }

    // ===================================================================
    // RT-R09 — recalculateBadge() sets rating as rounded average
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-R09 | recalculateBadge() sets provider rating to rounded 2-decimal average")
    void rtR09_recalculateBadge_setsRoundedAverageRating() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(4.256789));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(60L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getRating())
                .as("rating must be rounded to 2 decimal places")
                .isEqualTo(4.26);
    }

    // ===================================================================
    // RT-R10 — NONE badge when below all thresholds
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-R10 | recalculateBadge() assigns NONE when avg < 3.0 or jobs < 5")
    void rtR10_recalculateBadge_assignsNoneBadge() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(2.5));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(3L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.NONE);
    }

    // ===================================================================
    // RT-R11 — BRONZE badge (avg >= 3.0, jobs >= 5)
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-R11 | recalculateBadge() assigns BRONZE (avg >= 3.0, jobs >= 5)")
    void rtR11_recalculateBadge_assignsBronzeBadge() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(3.2));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(10L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.BRONZE);
    }

    // ===================================================================
    // RT-R12 — SILVER badge (avg >= 3.5, jobs >= 20)
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-R12 | recalculateBadge() assigns SILVER (avg >= 3.5, jobs >= 20)")
    void rtR12_recalculateBadge_assignsSilverBadge() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(3.7));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(25L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.SILVER);
    }

    // ===================================================================
    // RT-R13 — GOLD badge (avg >= 4.0, jobs >= 50)
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-R13 | recalculateBadge() assigns GOLD (avg >= 4.0, jobs >= 50)")
    void rtR13_recalculateBadge_assignsGoldBadge() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(4.2));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(55L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.GOLD);
    }

    // ===================================================================
    // RT-R14 — TOP_RATED badge (avg >= 4.5, jobs >= 100)
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-R14 | recalculateBadge() assigns TOP_RATED (avg >= 4.5, jobs >= 100)")
    void rtR14_recalculateBadge_assignsTopRatedBadge() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.of(4.8));
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(120L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.TOP_RATED);
    }

    // ===================================================================
    // RT-R15 — recalculateBadge() on unknown providerId throws ResourceNotFoundException
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-R15 | recalculateBadge() throws ResourceNotFoundException for unknown providerId")
    void rtR15_recalculateBadge_unknownProvider_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.recalculateBadge(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Provider not found");
    }

    // ===================================================================
    // RT-R16 — recalculateBadge() defaults to 0.0 when no reviews exist
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-R16 | recalculateBadge() defaults provider rating to 0.0 when no reviews exist")
    void rtR16_recalculateBadge_noReviews_defaultsRatingToZero() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(reviewRepository.findAverageRatingByProviderId(2L)).thenReturn(Optional.empty());
        when(bookingRepository.countByProviderIdAndStatus(2L, BookingStatus.COMPLETED)).thenReturn(0L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(provider);

        reviewService.recalculateBadge(2L);

        assertThat(captor.getValue().getRating())
                .as("rating must default to 0.0 when no reviews exist")
                .isEqualTo(0.0);
        assertThat(captor.getValue().getBadgeLevel()).isEqualTo(BadgeLevel.NONE);
    }

    // ===================================================================
    // RT-R17 — getProviderReviews() delegates to repository and returns list
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-R17 | getProviderReviews() returns all reviews for the given providerId")
    void rtR17_getProviderReviews_returnsFullList() {
        Review r1 = Review.builder().id(1L).booking(completedBooking)
                .homeowner(homeowner).provider(provider).rating(5).comment("Great!").build();
        Review r2 = Review.builder().id(2L).booking(completedBooking)
                .homeowner(homeowner).provider(provider).rating(4).comment("Good.").build();

        when(reviewRepository.findByProviderId(2L)).thenReturn(List.of(r1, r2));

        List<Review> result = reviewService.getProviderReviews(2L);

        assertThat(result).hasSize(2);
        verify(reviewRepository).findByProviderId(2L);
    }

    // ===================================================================
    // RT-R18 — Review default isFlagged is false on creation
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-R18 | Newly created review has isFlagged = false by default")
    void rtR18_review_defaultIsFlaggedIsFalse() {
        stubSuccessfulSubmit();
        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        when(reviewRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(1L, 50L, 5, "Wonderful!");

        assertThat(captor.getValue().isFlagged())
                .as("isFlagged must be false for a newly submitted review")
                .isFalse();
    }
}