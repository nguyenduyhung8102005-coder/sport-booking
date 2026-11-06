package com.sportbooking.service;

import com.sportbooking.config.JwtProperties;
import com.sportbooking.dto.request.LoginRequest;
import com.sportbooking.dto.request.RegisterRequest;
import com.sportbooking.dto.response.AuthResponse;
import com.sportbooking.entity.RefreshToken;
import com.sportbooking.entity.User;
import com.sportbooking.entity.enums.Role;
import com.sportbooking.entity.enums.UserStatus;
import com.sportbooking.exception.AppException;
import com.sportbooking.exception.ErrorCode;
import com.sportbooking.repository.RefreshTokenRepository;
import com.sportbooking.repository.UserRepository;
import com.sportbooking.security.JwtService;
import com.sportbooking.security.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit test: chỉ test logic của AuthService, mọi phụ thuộc (DB, JWT, BCrypt) đều là mock.
 * Không khởi động Spring, không cần database -> chạy trong vài mili giây.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T03:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("unused-in-this-test", Duration.ofMinutes(15), Duration.ofDays(7));
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder,
                jwtService, jwtProperties, fixedClock);
    }

    /** Tạo user giả như thể đã được lưu vào DB (có id). */
    private static User savedCustomer(Long id) {
        User user = User.newCustomer("an@gmail.com", "hashed-password", "Nguyễn An", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        void success_normalizesEmail_hashesPassword_andIssuesTokens() {
            when(userRepository.existsByEmail("an@gmail.com")).thenReturn(false);
            when(passwordEncoder.encode("Matkhau123")).thenReturn("hashed-password");
            when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                ReflectionTestUtils.setField(user, "id", 1L);   // giả lập DB sinh id
                return user;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");

            AuthResponse response = authService.register(
                    new RegisterRequest("  An@Gmail.com ", "Matkhau123", " Nguyễn An ", null));

            ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
            verify(userRepository).saveAndFlush(savedUser.capture());
            assertThat(savedUser.getValue().getEmail()).isEqualTo("an@gmail.com");
            assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
            assertThat(savedUser.getValue().getFullName()).isEqualTo("Nguyễn An");
            assertThat(savedUser.getValue().getRole()).isEqualTo(Role.CUSTOMER);
            assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

            assertThat(response.accessToken()).isEqualTo("access-token");
            assertThat(response.refreshToken()).isNotBlank();
            assertThat(response.expiresIn()).isEqualTo(15 * 60);
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void duplicateEmail_throwsConflict_andSavesNothing() {
            when(userRepository.existsByEmail("an@gmail.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(
                    new RegisterRequest("an@gmail.com", "Matkhau123", "An", null)))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

            verify(userRepository, never()).saveAndFlush(any());
            verifyNoInteractions(refreshTokenRepository);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        void wrongPassword_throwsInvalidCredentials() {
            when(userRepository.findByEmail("an@gmail.com")).thenReturn(Optional.of(savedCustomer(1L)));
            when(passwordEncoder.matches("sai-mat-khau", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("an@gmail.com", "sai-mat-khau")))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verifyNoInteractions(jwtService);
        }

        @Test
        void unknownEmail_throwsSameErrorAsWrongPassword() {
            when(userRepository.findByEmail("ai@gmail.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ai@gmail.com", "Matkhau123")))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void lockedAccount_throwsAccountLocked() {
            User locked = savedCustomer(1L);
            locked.changeStatus(UserStatus.LOCKED);
            when(userRepository.findByEmail("an@gmail.com")).thenReturn(Optional.of(locked));
            when(passwordEncoder.matches("Matkhau123", "hashed-password")).thenReturn(true);

            assertThatThrownBy(() -> authService.login(new LoginRequest("an@gmail.com", "Matkhau123")))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_LOCKED);
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    @DisplayName("refresh")
    class Refresh {

        @Test
        void validToken_revokesOldToken_andIssuesNewPair() {
            User user = savedCustomer(1L);
            RefreshToken oldToken = new RefreshToken(user, TokenUtils.sha256("old-token"), NOW.plus(Duration.ofDays(1)));
            when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256("old-token")))
                    .thenReturn(Optional.of(oldToken));
            when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");

            AuthResponse response = authService.refresh("old-token");

            assertThat(oldToken.isRevoked()).isTrue();
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isNotEqualTo("old-token");
            verify(refreshTokenRepository).save(any(RefreshToken.class));
        }

        @Test
        void reusedRevokedToken_revokesAllSessionsOfUser() {
            User user = savedCustomer(1L);
            RefreshToken stolenToken = new RefreshToken(user, TokenUtils.sha256("old-token"), NOW.plus(Duration.ofDays(1)));
            stolenToken.revoke(NOW.minusSeconds(60));   // đã được dùng 1 lần trước đó
            when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256("old-token")))
                    .thenReturn(Optional.of(stolenToken));

            assertThatThrownBy(() -> authService.refresh("old-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

            verify(refreshTokenRepository).revokeAllByUserId(1L, NOW);
            verifyNoInteractions(jwtService);
        }

        @Test
        void expiredToken_throwsInvalidRefreshToken() {
            RefreshToken expired = new RefreshToken(savedCustomer(1L), TokenUtils.sha256("old-token"), NOW.minusSeconds(1));
            when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.sha256("old-token")))
                    .thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> authService.refresh("old-token"))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
            verifyNoInteractions(jwtService);
        }
    }
}
