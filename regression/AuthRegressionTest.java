package com.example.FixItNow.regression;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.FixItNow.dto.request.LoginRequest;
import com.example.FixItNow.dto.request.RegisterRequest;
import com.example.FixItNow.dto.response.AuthResponse;
import com.example.FixItNow.entity.User;
import com.example.FixItNow.enums.UserType;
import com.example.FixItNow.exception.BadRequestException;
import com.example.FixItNow.repository.UserRepository;
import com.example.FixItNow.security.JwtTokenProvider;
import com.example.FixItNow.service.AuthService;

/**
 * =========================================================
 * REGRESSION TEST SUITE — Authentication (SRS FR1, FR2)
 * =========================================================
 *
 * PURPOSE:
 *   Ensures that user registration and login continue to work
 *   correctly after any code update. Covers HOMEOWNER, SERVICE_PROVIDER,
 *   and ADMIN registration flows, JWT token generation, duplicate
 *   checks, and invalid credential handling.
 *
 * HOW TO RUN IN VS CODE TERMINAL:
 *   cd "...path...\FixItNow"
 *   .\mvnw.cmd test -Dtest="com.example.FixItNow.regression.AuthRegressionTest"
 *
 * PLACEMENT:
 *   src/test/java/com/example/FixItNow/regression/AuthRegressionTest.java
 *
 * COVERED REGRESSION SCENARIOS:
 *   RT-A01  Homeowner registration saves user with correct fields
 *   RT-A02  Registered user password is BCrypt-hashed, never plain text
 *   RT-A03  New user is active by default after registration
 *   RT-A04  Duplicate email throws BadRequestException
 *   RT-A05  Duplicate username throws BadRequestException
 *   RT-A06  Duplicate email check does not save any user
 *   RT-A07  Service provider registration sets isVerified = false
 *   RT-A08  Service provider registration stores serviceCategory
 *   RT-A09  Admin registration stores department field
 *   RT-A10  Homeowner registration returns correct UserType
 *   RT-A11  Valid login returns AuthResponse with non-null JWT token
 *   RT-A12  Valid login returns correct userId, username, and email
 *   RT-A13  Token type is always "Bearer"
 *   RT-A14  Wrong password throws BadCredentialsException
 *   RT-A15  Unknown email throws BadCredentialsException
 *   RT-A16  Disabled account throws DisabledException
 *   RT-A17  Login via username (not email) works correctly
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("regression")
@DisplayName("Authentication Regression — Registration and Login still work after updates")
class AuthRegressionTest {

    // ------------------------------------------------------------------ mocks
    @Mock private UserRepository        userRepository;
    @Mock private PasswordEncoder       passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider      tokenProvider;

    @InjectMocks private AuthService authService;

    // ---------------------------------------------------------------- fixture
    private RegisterRequest homeownerRequest;
    private RegisterRequest providerRequest;
    private RegisterRequest adminRequest;
    private User            savedHomeowner;
    private User            savedProvider;

    @BeforeEach
    void setUp() {
        // Homeowner registration request
        homeownerRequest = new RegisterRequest();
        homeownerRequest.setName("Alice Smith");
        homeownerRequest.setEmail("alice@fixitnow.test");
        homeownerRequest.setUsername("alice123");
        homeownerRequest.setPassword("SecurePass1!");
        homeownerRequest.setPhone("0771234567");
        homeownerRequest.setAddress("12 Main St, Colombo");
        homeownerRequest.setUserType(UserType.HOMEOWNER);

        // Service provider registration request
        providerRequest = new RegisterRequest();
        providerRequest.setName("Bob Provider");
        providerRequest.setEmail("bob@fixitnow.test");
        providerRequest.setUsername("bob_plumber");
        providerRequest.setPassword("ProvPass99!");
        providerRequest.setUserType(UserType.SERVICE_PROVIDER);
        providerRequest.setServiceCategory("Plumbing");

        // Admin registration request
        adminRequest = new RegisterRequest();
        adminRequest.setName("Carol Admin");
        adminRequest.setEmail("carol@fixitnow.test");
        adminRequest.setUsername("carol_admin");
        adminRequest.setPassword("AdminPass1!");
        adminRequest.setUserType(UserType.ADMIN);
        adminRequest.setDepartment("Operations");

        // Pre-built saved user for login tests
        savedHomeowner = User.builder()
                .id(1L)
                .name("Alice Smith")
                .email("alice@fixitnow.test")
                .username("alice123")
                .passwordHash("$2a$bcrypt_hashed")
                .userType(UserType.HOMEOWNER)
                .isActive(true)
                .build();

        savedProvider = User.builder()
                .id(2L)
                .name("Bob Provider")
                .email("bob@fixitnow.test")
                .username("bob_plumber")
                .passwordHash("$2a$bcrypt_hashed_provider")
                .userType(UserType.SERVICE_PROVIDER)
                .isVerified(false)
                .isActive(true)
                .build();
    }

    // ===================================================================
    // RT-A01 — Homeowner registration saves user with correct fields
    // ===================================================================
    @Test
    @Order(1)
    @DisplayName("RT-A01 | Homeowner registration saves user with correct name, email, and username")
    void rtA01_register_homeowner_savesCorrectFields() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$bcrypt_hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedHomeowner);

        User result = authService.register(homeownerRequest);

        assertThat(result.getName()).isEqualTo("Alice Smith");
        assertThat(result.getEmail()).isEqualTo("alice@fixitnow.test");
        assertThat(result.getUsername()).isEqualTo("alice123");
    }

    // ===================================================================
    // RT-A02 — Password is BCrypt-hashed, never stored as plain text
    // ===================================================================
    @Test
    @Order(2)
    @DisplayName("RT-A02 | Password is BCrypt-encoded before saving — never stored as plain text")
    void rtA02_register_passwordIsBcryptHashed() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode("SecurePass1!")).thenReturn("$2a$bcrypt_hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedHomeowner);

        authService.register(homeownerRequest);

        assertThat(captor.getValue().getPasswordHash())
                .as("password must be hashed, not plain text")
                .isEqualTo("$2a$bcrypt_hashed")
                .doesNotContain("SecurePass1!");
    }

    // ===================================================================
    // RT-A03 — New user is active by default
    // ===================================================================
    @Test
    @Order(3)
    @DisplayName("RT-A03 | Newly registered user has isActive = true by default")
    void rtA03_register_newUserIsActiveByDefault() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedHomeowner);

        authService.register(homeownerRequest);

        assertThat(captor.getValue().isActive())
                .as("user must be active upon registration")
                .isTrue();
    }

    // ===================================================================
    // RT-A04 — Duplicate email throws BadRequestException
    // ===================================================================
    @Test
    @Order(4)
    @DisplayName("RT-A04 | Registration with duplicate email throws BadRequestException")
    void rtA04_register_duplicateEmail_throwsBadRequestException() {
        when(userRepository.existsByEmail("alice@fixitnow.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(homeownerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already registered");
    }

    // ===================================================================
    // RT-A05 — Duplicate username throws BadRequestException
    // ===================================================================
    @Test
    @Order(5)
    @DisplayName("RT-A05 | Registration with duplicate username throws BadRequestException")
    void rtA05_register_duplicateUsername_throwsBadRequestException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("alice123")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(homeownerRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already taken");
    }

    // ===================================================================
    // RT-A06 — Duplicate check prevents any save call
    // ===================================================================
    @Test
    @Order(6)
    @DisplayName("RT-A06 | Duplicate email check prevents userRepository.save() from being called")
    void rtA06_register_duplicateEmail_neverCallsSave() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(homeownerRequest))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    // ===================================================================
    // RT-A07 — Service provider starts as unverified
    // ===================================================================
    @Test
    @Order(7)
    @DisplayName("RT-A07 | Service provider registration sets isVerified = false (pending admin approval)")
    void rtA07_register_serviceProvider_isUnverifiedByDefault() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedProvider);

        authService.register(providerRequest);

        assertThat(captor.getValue().isVerified())
                .as("provider must start unverified — admin must approve first")
                .isFalse();
    }

    // ===================================================================
    // RT-A08 — Service provider stores serviceCategory
    // ===================================================================
    @Test
    @Order(8)
    @DisplayName("RT-A08 | Service provider registration stores serviceCategory correctly")
    void rtA08_register_serviceProvider_storesServiceCategory() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedProvider);

        authService.register(providerRequest);

        assertThat(captor.getValue().getServiceCategory())
                .as("serviceCategory must be saved on provider")
                .isEqualTo("Plumbing");
    }

    // ===================================================================
    // RT-A09 — Admin registration stores department
    // ===================================================================
    @Test
    @Order(9)
    @DisplayName("RT-A09 | Admin registration stores department field correctly")
    void rtA09_register_admin_storesDepartment() {
        User savedAdmin = User.builder()
                .id(3L).name("Carol Admin").email("carol@fixitnow.test")
                .username("carol_admin").passwordHash("$2a$hashed")
                .userType(UserType.ADMIN).department("Operations").isActive(true).build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedAdmin);

        authService.register(adminRequest);

        assertThat(captor.getValue().getDepartment())
                .as("department must be stored for admin users")
                .isEqualTo("Operations");
    }

    // ===================================================================
    // RT-A10 — Homeowner UserType is preserved on save
    // ===================================================================
    @Test
    @Order(10)
    @DisplayName("RT-A10 | Homeowner registration saves UserType.HOMEOWNER correctly")
    void rtA10_register_homeowner_correctUserType() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenReturn(savedHomeowner);

        authService.register(homeownerRequest);

        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.HOMEOWNER);
    }

    // ===================================================================
    // RT-A11 — Valid login returns non-null JWT token
    // ===================================================================
    @Test
    @Order(11)
    @DisplayName("RT-A11 | Valid login credentials return an AuthResponse with a non-null JWT token")
    void rtA11_login_validCredentials_returnsJwtToken() {
        LoginRequest req = buildLoginRequest("alice@fixitnow.test", "SecurePass1!");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("eyJ.mock.jwt.token");
        when(userRepository.findByEmail("alice@fixitnow.test")).thenReturn(Optional.of(savedHomeowner));

        AuthResponse response = authService.login(req);

        assertThat(response.getToken())
                .as("JWT token must not be null or empty on successful login")
                .isNotNull()
                .isNotBlank();
    }

    // ===================================================================
    // RT-A12 — Valid login returns correct user details
    // ===================================================================
    @Test
    @Order(12)
    @DisplayName("RT-A12 | Valid login returns correct userId, username, and email in AuthResponse")
    void rtA12_login_validCredentials_returnsCorrectUserDetails() {
        LoginRequest req = buildLoginRequest("alice@fixitnow.test", "SecurePass1!");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("eyJ.mock.jwt.token");
        when(userRepository.findByEmail("alice@fixitnow.test")).thenReturn(Optional.of(savedHomeowner));

        AuthResponse response = authService.login(req);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("alice123");
        assertThat(response.getEmail()).isEqualTo("alice@fixitnow.test");
    }

    // ===================================================================
    // RT-A13 — Token type is always "Bearer"
    // ===================================================================
    @Test
    @Order(13)
    @DisplayName("RT-A13 | AuthResponse tokenType is always 'Bearer'")
    void rtA13_login_tokenTypeIsBearer() {
        LoginRequest req = buildLoginRequest("alice@fixitnow.test", "SecurePass1!");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("eyJ.mock.jwt.token");
        when(userRepository.findByEmail("alice@fixitnow.test")).thenReturn(Optional.of(savedHomeowner));

        AuthResponse response = authService.login(req);

        assertThat(response.getTokenType())
                .as("token type must always be Bearer for JWT")
                .isEqualTo("Bearer");
    }

    // ===================================================================
    // RT-A14 — Wrong password throws BadCredentialsException
    // ===================================================================
    @Test
    @Order(14)
    @DisplayName("RT-A14 | Wrong password throws BadCredentialsException")
    void rtA14_login_wrongPassword_throwsBadCredentialsException() {
        LoginRequest req = buildLoginRequest("alice@fixitnow.test", "WrongPassword!");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ===================================================================
    // RT-A15 — Unknown email throws BadCredentialsException
    // ===================================================================
    @Test
    @Order(15)
    @DisplayName("RT-A15 | Login with unknown email throws BadCredentialsException")
    void rtA15_login_unknownEmail_throwsBadCredentialsException() {
        LoginRequest req = buildLoginRequest("nobody@fixitnow.test", "AnyPass1!");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("User not found"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ===================================================================
    // RT-A16 — Disabled account throws DisabledException
    // ===================================================================
    @Test
    @Order(16)
    @DisplayName("RT-A16 | Login on a disabled/blacklisted account throws DisabledException")
    void rtA16_login_disabledAccount_throwsDisabledException() {
        LoginRequest req = buildLoginRequest("alice@fixitnow.test", "SecurePass1!");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("Account is disabled"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(DisabledException.class);
    }

    // ===================================================================
    // RT-A17 — Login via username (not email) works correctly
    // ===================================================================
    @Test
    @Order(17)
    @DisplayName("RT-A17 | Login using username instead of email still returns a valid AuthResponse")
    void rtA17_login_withUsername_returnsValidAuthResponse() {
        LoginRequest req = buildLoginRequest("alice123", "SecurePass1!");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("eyJ.mock.jwt.token");
        // Email lookup returns empty, falls back to username
        when(userRepository.findByEmail("alice123")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("alice123")).thenReturn(Optional.of(savedHomeowner));

        AuthResponse response = authService.login(req);

        assertThat(response.getToken()).isNotNull();
        assertThat(response.getUsername()).isEqualTo("alice123");
    }

    // ----------------------------------------------------------------- helper
    private LoginRequest buildLoginRequest(String usernameOrEmail, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail(usernameOrEmail);
        req.setPassword(password);
        return req;
    }
}