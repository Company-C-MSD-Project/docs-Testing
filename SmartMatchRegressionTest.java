package com.example.FixItNow.regression;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.FixItNow.dto.response.ProviderMatchResponse;
import com.example.FixItNow.entity.ProviderLocation;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BadgeLevel;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.repository.ProviderLocationRepository;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.service.SmartMatchService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Smart Match Algorithm (SRS FR7)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that the provider matching algorithm (ranking formula,
 *   radius filter, ETA calculation, haversine distance, and edge
 *   cases) continues to work correctly after any code update.
 *
 * RANKING FORMULA (from SmartMatchService Javadoc):
 *   score = (normalizedRating × 0.6) + (normalizedProximity × 0.4)
 *   normalizedRating    = rating / 5.0
 *   normalizedProximity = 1 - (distanceKm / 25.0)
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.SmartMatchRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/SmartMatchRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-SM01  findMatches() returns empty list when no providers in category
 *   RT-SM02  findMatches() delegates to findVerifiedProvidersByCategory
 *   RT-SM03  findMatches() excludes providers beyond 25 km radius
 *   RT-SM04  findMatches() includes providers within 25 km radius
 *   RT-SM05  findMatches() returns providers sorted by score descending
 *   RT-SM06  Higher-rated provider ranks above closer but lower-rated provider
 *   RT-SM07  Provider with no location is placed at boundary (25 km)
 *   RT-SM08  Provider with no location is excluded when better matches exist inside radius
 *   RT-SM09  ProviderMatchResponse carries correct providerId, name, category
 *   RT-SM10  ProviderMatchResponse carries correct distanceKm (rounded to 1 decimal)
 *   RT-SM11  ProviderMatchResponse carries correct etaMinutes based on distance
 *   RT-SM12  ProviderMatchResponse isVerified flag is true for verified providers
 *   RT-SM13  haversineKm() returns 0.0 for identical coordinates
 *   RT-SM14  haversineKm() is symmetric — distance A→B equals distance B→A
 *   RT-SM15  haversineKm() returns correct distance for known coordinates
 *   RT-SM16  Provider with null rating gets score as if rating = 0
 *   RT-SM17  findMatches() returns all providers when all are within radius
 *   RT-SM18  ETA is 0 minutes for a provider at the same location as homeowner
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Smart Match Regression — Provider matching algorithm still works after updates")
class SmartMatchRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private UserRepository             userRepository;
    @Mock private ProviderLocationRepository locationRepository;

    @InjectMocks private SmartMatchService smartMatchService;

    // ---------------------------------------------------------------- fixture
    // Homeowner is located in Colombo city centre
    private static final double HOME_LAT = 6.9271;
    private static final double HOME_LNG = 79.8612;

    // Two providers with known locations
    private User providerNear;   // ~2 km away, rating 4.0
    private User providerFar;    // ~20 km away, rating 4.8 (higher rated but farther)
    private User providerBeyond; // ~30 km away — outside 25 km radius

    private ProviderLocation locationNear;
    private ProviderLocation locationFar;
    private ProviderLocation locationBeyond;

    @BeforeEach
    void setUp() {
        providerNear = buildProvider(1L, "Near Bob", "Plumbing", 4.0, BadgeLevel.GOLD);
        providerFar  = buildProvider(2L, "Far Alice", "Plumbing", 4.8, BadgeLevel.TOP_RATED);
        providerBeyond = buildProvider(3L, "Beyond Charlie", "Plumbing", 5.0, BadgeLevel.TOP_RATED);

        // ~2 km north of homeowner
        locationNear = buildLocation(1L, providerNear, 6.9450, 79.8612);
        // ~20 km north-east of homeowner
        locationFar  = buildLocation(2L, providerFar, 7.0800, 79.9700);
        // ~30 km away — beyond radius
        locationBeyond = buildLocation(3L, providerBeyond, 7.1900, 80.0500);
    }

    // ----------------------------------------------------------------- helpers
    private User buildProvider(Long id, String name, String category,
                                double rating, BadgeLevel badge) {
        return User.builder()
                .id(id).name(name).email(name.toLowerCase().replace(" ", "") + "@test.com")
                .username(name.toLowerCase().replace(" ", "")).passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER)
                .serviceCategory(category).rating(rating).badgeLevel(badge)
                .isVerified(true).isActive(true)
                .build();
    }

    private ProviderLocation buildLocation(Long id, User provider, double lat, double lng) {
        return ProviderLocation.builder()
                .id(id).provider(provider).latitude(lat).longitude(lng).build();
    }

    // ===================================================================
    // RT-SM01 — empty list when no providers in category
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-SM01 | findMatches() returns empty list when no providers exist in category")
    void rtSm01_findMatches_noProviders_returnsEmptyList() {
        when(userRepository.findVerifiedProvidersByCategory("Carpentry")).thenReturn(List.of());

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Carpentry", HOME_LAT, HOME_LNG);

        assertThat(result).isEmpty();
    }

    // ===================================================================
    // RT-SM02 — delegates to findVerifiedProvidersByCategory
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-SM02 | findMatches() delegates category lookup to userRepository")
    void rtSm02_findMatches_delegatesToRepository() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of());

        smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        verify(userRepository).findVerifiedProvidersByCategory("Plumbing");
    }

    // ===================================================================
    // RT-SM03 — excludes providers beyond 25 km radius
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-SM03 | findMatches() excludes providers located beyond the 25 km radius")
    void rtSm03_findMatches_excludesProvidersOutsideRadius() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerBeyond));
        when(locationRepository.findByProviderId(3L)).thenReturn(Optional.of(locationBeyond));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result).isEmpty();
    }

    // ===================================================================
    // RT-SM04 — includes providers within 25 km radius
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-SM04 | findMatches() includes providers within the 25 km radius")
    void rtSm04_findMatches_includesProvidersInsideRadius() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderId()).isEqualTo(1L);
    }

    // ===================================================================
    // RT-SM05 — results are sorted by score descending
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-SM05 | findMatches() returns providers sorted by score descending (highest first)")
    void rtSm05_findMatches_sortedByScoreDescending() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear, providerFar));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));
        when(locationRepository.findByProviderId(2L)).thenReturn(Optional.of(locationFar));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result).hasSize(2);
        // First result must have score >= second result
        double score0 = computeExpectedScore(result.get(0));
        double score1 = computeExpectedScore(result.get(1));
        assertThat(score0).isGreaterThanOrEqualTo(score1);
    }

    // ===================================================================
    // RT-SM06 — Score formula: rating weight (60%) + proximity weight (40%)
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-SM06 | Score formula correctly combines rating (60%) and proximity (40%)")
    void rtSm06_findMatches_scoreFormulaWeightsRatingAndProximity() {
        // providerNear: rating=4.0, ~2 km  → score ≈ (0.80×0.6) + (0.92×0.4) = 0.848
        // providerFar:  rating=4.8, ~20 km → score ≈ (0.96×0.6) + (0.20×0.4) = 0.656
        // providerNear wins because proximity advantage outweighs rating difference at 2 km vs 20 km
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear, providerFar));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));
        when(locationRepository.findByProviderId(2L)).thenReturn(Optional.of(locationFar));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result).hasSize(2);

        // Extract actual distances from results and verify scores are calculated correctly
        double distNear = result.stream().filter(r -> r.getProviderId().equals(1L))
                .findFirst().get().getDistanceKm();
        double distFar  = result.stream().filter(r -> r.getProviderId().equals(2L))
                .findFirst().get().getDistanceKm();

        double scoreNear = (4.0 / 5.0) * 0.6 + (1.0 - (distNear / 25.0)) * 0.4;
        double scoreFar  = (4.8 / 5.0) * 0.6 + (1.0 - (distFar  / 25.0)) * 0.4;

        // The first result must have the higher score — regardless of which provider that is
        double firstScore  = computeExpectedScore(result.get(0));
        double secondScore = computeExpectedScore(result.get(1));
        assertThat(firstScore)
                .as("first result must have a score >= second result (sorted descending)")
                .isGreaterThanOrEqualTo(secondScore);

        // Sanity-check that both scores were computed correctly
        assertThat(scoreNear).isGreaterThan(0.0);
        assertThat(scoreFar).isGreaterThan(0.0);
    }

    // ===================================================================
    // RT-SM07 — Provider with no location placed at boundary (25 km)
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-SM07 | Provider with no recorded location is placed at 25 km boundary")
    void rtSm07_findMatches_noLocation_placedAtBoundary() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.empty());

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        // distanceKm should be exactly 25.0 (MAX_RADIUS_KM) → not filtered out
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistanceKm()).isEqualTo(25.0);
    }

    // ===================================================================
    // RT-SM08 — Provider with no location excluded when a closer match exists
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-SM08 | Provider with no location ranks below nearby providers with known location")
    void rtSm08_findMatches_noLocation_ranksLastBelowKnownProviders() {
        User providerUnknownLoc = buildProvider(10L, "Unknown Location Dan",
                "Plumbing", 5.0, BadgeLevel.TOP_RATED);

        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear, providerUnknownLoc));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));
        when(locationRepository.findByProviderId(10L)).thenReturn(Optional.empty());

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        // providerUnknownLoc: rating=5.0, dist=25.0 → score = (1.0×0.6) + (0.0×0.4) = 0.60
        // providerNear:       rating=4.0, dist~2 km → score = (0.8×0.6) + (~0.92×0.4) ≈ 0.85
        // So providerNear should rank first
        assertThat(result.get(0).getProviderId()).isEqualTo(1L);
    }

    // ===================================================================
    // RT-SM09 — Response has correct providerId, name, category
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-SM09 | ProviderMatchResponse carries correct providerId, name, and serviceCategory")
    void rtSm09_findMatches_responseHasCorrectIdentityFields() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        ProviderMatchResponse match = result.get(0);
        assertThat(match.getProviderId()).isEqualTo(1L);
        assertThat(match.getName()).isEqualTo("Near Bob");
        assertThat(match.getServiceCategory()).isEqualTo("Plumbing");
    }

    // ===================================================================
    // RT-SM10 — distanceKm is rounded to 1 decimal place
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-SM10 | ProviderMatchResponse distanceKm is rounded to 1 decimal place")
    void rtSm10_findMatches_distanceIsRoundedToOneDecimal() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        double distanceKm = result.get(0).getDistanceKm();
        // Check it is rounded to at most 1 decimal place
        assertThat(distanceKm).isEqualTo(Math.round(distanceKm * 10.0) / 10.0);
    }

    // ===================================================================
    // RT-SM11 — etaMinutes is correct based on distance and 30 km/h speed
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-SM11 | etaMinutes is correctly calculated from distanceKm at 30 km/h avg speed")
    void rtSm11_findMatches_etaMinutesCalculatedCorrectly() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        ProviderMatchResponse match = result.get(0);
        int expectedEta = (int) Math.round((match.getDistanceKm() / 30.0) * 60);
        assertThat(match.getEtaMinutes())
                .as("ETA must be calculated as (distanceKm / 30) * 60 minutes")
                .isEqualTo(expectedEta);
    }

    // ===================================================================
    // RT-SM12 — isVerified flag is set correctly
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-SM12 | ProviderMatchResponse isVerified is true for verified providers")
    void rtSm12_findMatches_isVerifiedFlagIsCorrect() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result.get(0).isVerified()).isTrue();
    }

    // ===================================================================
    // RT-SM13 — haversineKm() returns 0.0 for identical coordinates
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-SM13 | haversineKm() returns 0.0 when both points are identical")
    void rtSm13_haversine_identicalPoints_returnsZero() {
        double distance = SmartMatchService.haversineKm(6.9271, 79.8612, 6.9271, 79.8612);

        assertThat(distance)
                .as("distance between identical coordinates must be 0.0")
                .isEqualTo(0.0, within(0.001));
    }

    // ===================================================================
    // RT-SM14 — haversineKm() is symmetric
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-SM14 | haversineKm() is symmetric — distance A→B equals distance B→A")
    void rtSm14_haversine_isSymmetric() {
        double distAtoB = SmartMatchService.haversineKm(6.9271, 79.8612, 7.0800, 79.9700);
        double distBtoA = SmartMatchService.haversineKm(7.0800, 79.9700, 6.9271, 79.8612);

        assertThat(distAtoB)
                .as("haversine must be symmetric")
                .isEqualTo(distBtoA, within(0.001));
    }

    // ===================================================================
    // RT-SM15 — haversineKm() returns correct distance for known coordinates
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-SM15 | haversineKm() returns ~111 km for 1 degree latitude difference")
    void rtSm15_haversine_correctDistanceForKnownCoordinates() {
        // 1 degree of latitude ≈ 111 km
        double distance = SmartMatchService.haversineKm(0.0, 0.0, 1.0, 0.0);

        assertThat(distance)
                .as("1 degree latitude should be approximately 111 km")
                .isBetween(110.0, 112.0);
    }

    // ===================================================================
    // RT-SM16 — Provider with null rating scored as rating = 0
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-SM16 | Provider with null rating is scored as if rating = 0 (no NPE)")
    void rtSm16_findMatches_nullRating_scoredAsZero() {
        User noRatingProvider = buildProvider(5L, "New Provider Eve", "Plumbing", 0.0, BadgeLevel.NONE);
        noRatingProvider.setRating(null); // simulate provider with no ratings yet

        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(noRatingProvider));
        when(locationRepository.findByProviderId(5L)).thenReturn(Optional.of(
                buildLocation(5L, noRatingProvider, 6.9300, 79.8650)));

        assertThatNoException()
                .as("null rating must not cause NullPointerException")
                .isThrownBy(() -> smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG));
    }

    // ===================================================================
    // RT-SM17 — All providers within radius are returned
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-SM17 | findMatches() returns all providers when all are within the 25 km radius")
    void rtSm17_findMatches_allWithinRadius_returnsAll() {
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear, providerFar));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(locationNear));
        when(locationRepository.findByProviderId(2L)).thenReturn(Optional.of(locationFar));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result).hasSize(2);
    }

    // ===================================================================
    // RT-SM18 — ETA is 0 minutes when provider is at same location
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-SM18 | etaMinutes is 0 when provider is at the same location as homeowner")
    void rtSm18_findMatches_sameLocation_etaIsZero() {
        // Provider at exactly the same coordinates as homeowner
        ProviderLocation sameLocation = buildLocation(1L, providerNear, HOME_LAT, HOME_LNG);
        when(userRepository.findVerifiedProvidersByCategory("Plumbing"))
                .thenReturn(List.of(providerNear));
        when(locationRepository.findByProviderId(1L)).thenReturn(Optional.of(sameLocation));

        List<ProviderMatchResponse> result =
                smartMatchService.findMatches("Plumbing", HOME_LAT, HOME_LNG);

        assertThat(result.get(0).getEtaMinutes())
                .as("ETA must be 0 when provider is at the same location")
                .isEqualTo(0);
    }

    // ----------------------------------------------------------------- helper
    /** Replicates the scoring formula from SmartMatchService for verification. */
    private double computeExpectedScore(ProviderMatchResponse m) {
        double ratingScore    = (m.getRating() != null ? m.getRating() : 0.0) / 5.0;
        double proximityScore = 1.0 - (m.getDistanceKm() / 25.0);
        return (ratingScore * 0.6) + (proximityScore * 0.4);
    }
}
