package com.example.FixItNow.regression;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.BadgeLevel;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.exception.ResourceNotFoundException;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.service.UserService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — User Management (SRS FR3, FR5)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that user profile updates, account suspension,
 *   blacklisting, provider verification, and user lookups
 *   continue to work correctly after any code update.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.UserManagementRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/UserManagementRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-U01  findById() returns correct user for a known id
 *   RT-U02  findById() throws ResourceNotFoundException for unknown id
 *   RT-U03  findByEmail() returns correct user for a known email
 *   RT-U04  findByEmail() throws ResourceNotFoundException for unknown email
 *   RT-U05  updateProfile() updates name when provided
 *   RT-U06  updateProfile() updates phone when provided
 *   RT-U07  updateProfile() updates address when provided
 *   RT-U08  updateProfile() does NOT overwrite fields that are null in the update
 *   RT-U09  updateProfile() saves and returns the updated user
 *   RT-U10  updateProfile() throws ResourceNotFoundException for unknown userId
 *   RT-U11  setActiveStatus(false) deactivates a user account
 *   RT-U12  setActiveStatus(true) reactivates a suspended user account
 *   RT-U13  setActiveStatus() saves the updated status
 *   RT-U14  blacklistHomeowner() sets isBlacklisted = true
 *   RT-U15  blacklistHomeowner() also sets isActive = false
 *   RT-U16  blacklistHomeowner() on a SERVICE_PROVIDER throws BadRequestException
 *   RT-U17  blacklistHomeowner() on an ADMIN throws BadRequestException
 *   RT-U18  blacklistHomeowner() throws ResourceNotFoundException for unknown id
 *   RT-U19  verifyProvider() sets isVerified = true
 *   RT-U20  verifyProvider() on a HOMEOWNER throws BadRequestException
 *   RT-U21  verifyProvider() on an ADMIN throws BadRequestException
 *   RT-U22  verifyProvider() throws ResourceNotFoundException for unknown id
 *   RT-U23  getAllUsers() delegates to repository and returns full list
 *   RT-U24  getUsersByType() delegates to repository with correct UserType
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("User Management Regression — Profile, suspension, and verification still work after updates")
class UserManagementRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    // ---------------------------------------------------------------- fixture
    private User homeowner;
    private User provider;
    private User admin;

    @BeforeEach
    void setUp() {
        homeowner = User.builder()
                .id(1L).name("Alice Smith").email("alice@fixitnow.test")
                .username("alice").passwordHash("hashed")
                .phone("0771234567").address("12 Main St, Colombo")
                .userType(UserType.HOMEOWNER).isActive(true).isBlacklisted(false)
                .build();

        provider = User.builder()
                .id(2L).name("Bob Provider").email("bob@fixitnow.test")
                .username("bob").passwordHash("hashed")
                .userType(UserType.SERVICE_PROVIDER)
                .serviceCategory("Plumbing").isVerified(false).isActive(true)
                .rating(4.2).badgeLevel(BadgeLevel.GOLD)
                .build();

        admin = User.builder()
                .id(3L).name("Carol Admin").email("carol@fixitnow.test")
                .username("carol").passwordHash("hashed")
                .userType(UserType.ADMIN).isActive(true)
                .department("Operations").accessLevel(2)
                .build();
    }

    // ===================================================================
    // RT-U01 — findById() returns correct user
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-U01 | findById() returns the correct user for a known id")
    void rtU01_findById_returnsCorrectUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));

        User result = userService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@fixitnow.test");
    }

    // ===================================================================
    // RT-U02 — findById() throws ResourceNotFoundException for unknown id
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-U02 | findById() throws ResourceNotFoundException for unknown userId")
    void rtU02_findById_unknownId_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ===================================================================
    // RT-U03 — findByEmail() returns correct user
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-U03 | findByEmail() returns the correct user for a known email")
    void rtU03_findByEmail_returnsCorrectUser() {
        when(userRepository.findByEmail("alice@fixitnow.test")).thenReturn(Optional.of(homeowner));

        User result = userService.findByEmail("alice@fixitnow.test");

        assertThat(result.getName()).isEqualTo("Alice Smith");
        assertThat(result.getUserType()).isEqualTo(UserType.HOMEOWNER);
    }

    // ===================================================================
    // RT-U04 — findByEmail() throws ResourceNotFoundException for unknown email
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-U04 | findByEmail() throws ResourceNotFoundException for unknown email")
    void rtU04_findByEmail_unknownEmail_throwsResourceNotFoundException() {
        when(userRepository.findByEmail("nobody@fixitnow.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("nobody@fixitnow.test"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ===================================================================
    // RT-U05 — updateProfile() updates name
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-U05 | updateProfile() updates the user's name when provided")
    void rtU05_updateProfile_updatesName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().name("Alice Johnson").build();
        userService.updateProfile(1L, updates);

        assertThat(captor.getValue().getName()).isEqualTo("Alice Johnson");
    }

    // ===================================================================
    // RT-U06 — updateProfile() updates phone
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-U06 | updateProfile() updates the user's phone when provided")
    void rtU06_updateProfile_updatesPhone() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().phone("0779876543").build();
        userService.updateProfile(1L, updates);

        assertThat(captor.getValue().getPhone()).isEqualTo("0779876543");
    }

    // ===================================================================
    // RT-U07 — updateProfile() updates address
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-U07 | updateProfile() updates the user's address when provided")
    void rtU07_updateProfile_updatesAddress() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().address("45 New Rd, Kandy").build();
        userService.updateProfile(1L, updates);

        assertThat(captor.getValue().getAddress()).isEqualTo("45 New Rd, Kandy");
    }

    // ===================================================================
    // RT-U08 — updateProfile() does NOT overwrite null fields
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-U08 | updateProfile() does NOT overwrite existing fields when update value is null")
    void rtU08_updateProfile_doesNotOverwriteNullFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Only update phone — name and address should stay unchanged
        User updates = User.builder().phone("0771111111").build();
        userService.updateProfile(1L, updates);

        assertThat(captor.getValue().getName())
                .as("name must not be overwritten when null in update")
                .isEqualTo("Alice Smith");
        assertThat(captor.getValue().getAddress())
                .as("address must not be overwritten when null in update")
                .isEqualTo("12 Main St, Colombo");
        assertThat(captor.getValue().getPhone())
                .as("phone must be updated")
                .isEqualTo("0771111111");
    }

    // ===================================================================
    // RT-U09 — updateProfile() saves and returns updated user
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-U09 | updateProfile() saves the updated user and returns it")
    void rtU09_updateProfile_savesAndReturnsUpdatedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User updates = User.builder().name("Alice Updated").build();
        User result = userService.updateProfile(1L, updates);

        verify(userRepository, times(1)).save(any(User.class));
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice Updated");
    }

    // ===================================================================
    // RT-U10 — updateProfile() throws ResourceNotFoundException for unknown id
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-U10 | updateProfile() throws ResourceNotFoundException for unknown userId")
    void rtU10_updateProfile_unknownId_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        User updates = User.builder().name("Ghost User").build();
        assertThatThrownBy(() -> userService.updateProfile(999L, updates))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-U11 — setActiveStatus(false) deactivates user
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-U11 | setActiveStatus(false) sets isActive = false (suspends the account)")
    void rtU11_setActiveStatus_false_deactivatesUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActiveStatus(1L, false);

        assertThat(captor.getValue().isActive())
                .as("isActive must be false after suspension")
                .isFalse();
    }

    // ===================================================================
    // RT-U12 — setActiveStatus(true) reactivates user
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-U12 | setActiveStatus(true) sets isActive = true (reactivates the account)")
    void rtU12_setActiveStatus_true_reactivatesUser() {
        homeowner.setActive(false); // start as suspended
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActiveStatus(1L, true);

        assertThat(captor.getValue().isActive())
                .as("isActive must be true after reactivation")
                .isTrue();
    }

    // ===================================================================
    // RT-U13 — setActiveStatus() saves the updated status
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-U13 | setActiveStatus() persists the status change to the repository")
    void rtU13_setActiveStatus_savesUpdatedStatus() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.setActiveStatus(2L, false);

        verify(userRepository, times(1)).save(any(User.class));
    }

    // ===================================================================
    // RT-U14 — blacklistHomeowner() sets isBlacklisted = true
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-U14 | blacklistHomeowner() sets isBlacklisted = true on the homeowner")
    void rtU14_blacklistHomeowner_setsIsBlacklistedTrue() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.blacklistHomeowner(1L);

        assertThat(captor.getValue().isBlacklisted())
                .as("isBlacklisted must be true after blacklisting")
                .isTrue();
    }

    // ===================================================================
    // RT-U15 — blacklistHomeowner() also deactivates the account
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-U15 | blacklistHomeowner() also sets isActive = false")
    void rtU15_blacklistHomeowner_alsoDeactivatesAccount() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.blacklistHomeowner(1L);

        assertThat(captor.getValue().isActive())
                .as("isActive must be false when homeowner is blacklisted")
                .isFalse();
    }

    // ===================================================================
    // RT-U16 — blacklistHomeowner() on SERVICE_PROVIDER throws BadRequestException
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-U16 | blacklistHomeowner() throws BadRequestException when called on a SERVICE_PROVIDER")
    void rtU16_blacklistHomeowner_serviceProvider_throwsBadRequestException() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));

        assertThatThrownBy(() -> userService.blacklistHomeowner(2L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only homeowners can be blacklisted");

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-U17 — blacklistHomeowner() on ADMIN throws BadRequestException
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-U17 | blacklistHomeowner() throws BadRequestException when called on an ADMIN")
    void rtU17_blacklistHomeowner_admin_throwsBadRequestException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.blacklistHomeowner(3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only homeowners can be blacklisted");

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-U18 — blacklistHomeowner() throws ResourceNotFoundException for unknown id
    // ===================================================================
    @Test
    @Order(18)
    @DisplayName("RT-U18 | blacklistHomeowner() throws ResourceNotFoundException for unknown userId")
    void rtU18_blacklistHomeowner_unknownId_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.blacklistHomeowner(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ===================================================================
    // RT-U19 — verifyProvider() sets isVerified = true
    // ===================================================================
    @Test
    @Order(19)
    @DisplayName("RT-U19 | verifyProvider() sets isVerified = true on the service provider")
    void rtU19_verifyProvider_setsIsVerifiedTrue() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.verifyProvider(2L);

        assertThat(captor.getValue().isVerified())
                .as("isVerified must be true after admin approval")
                .isTrue();
    }

    // ===================================================================
    // RT-U20 — verifyProvider() on HOMEOWNER throws BadRequestException
    // ===================================================================
    @Test
    @Order(20)
    @DisplayName("RT-U20 | verifyProvider() throws BadRequestException when called on a HOMEOWNER")
    void rtU20_verifyProvider_homeowner_throwsBadRequestException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(homeowner));

        assertThatThrownBy(() -> userService.verifyProvider(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a service provider");

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-U21 — verifyProvider() on ADMIN throws BadRequestException
    // ===================================================================
    @Test
    @Order(21)
    @DisplayName("RT-U21 | verifyProvider() throws BadRequestException when called on an ADMIN")
    void rtU21_verifyProvider_admin_throwsBadRequestException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.verifyProvider(3L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a service provider");

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-U22 — verifyProvider() throws ResourceNotFoundException for unknown id
    // ===================================================================
    @Test
    @Order(22)
    @DisplayName("RT-U22 | verifyProvider() throws ResourceNotFoundException for unknown userId")
    void rtU22_verifyProvider_unknownId_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.verifyProvider(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ===================================================================
    // RT-U23 — getAllUsers() delegates to repository
    // ===================================================================
    @Test
    @Order(23)
    @DisplayName("RT-U23 | getAllUsers() returns the full list from the repository")
    void rtU23_getAllUsers_returnsFullList() {
        when(userRepository.findAll()).thenReturn(List.of(homeowner, provider, admin));

        List<User> result = userService.getAllUsers();

        assertThat(result).hasSize(3);
        verify(userRepository).findAll();
    }

    // ===================================================================
    // RT-U24 — getUsersByType() delegates with correct UserType
    // ===================================================================
    @Test
    @Order(24)
    @DisplayName("RT-U24 | getUsersByType() delegates to repository with the correct UserType filter")
    void rtU24_getUsersByType_delegatesWithCorrectType() {
        when(userRepository.findByUserType(UserType.SERVICE_PROVIDER))
                .thenReturn(List.of(provider));

        List<User> result = userService.getUsersByType(UserType.SERVICE_PROVIDER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserType()).isEqualTo(UserType.SERVICE_PROVIDER);
        verify(userRepository).findByUserType(UserType.SERVICE_PROVIDER);
        // Must NOT call findAll or any other query
        verify(userRepository, never()).findAll();
    }
}
