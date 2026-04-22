package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.token.RefreshToken;
import com.echo.domain.user.User;
import com.echo.dto.request.ChangePasswordRequest;
import com.echo.dto.request.LoginRequest;
import com.echo.dto.request.RegisterRequest;
import com.echo.dto.response.AuthResponse;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.RefreshTokenRepository;
import com.echo.repository.UserRepository;
import com.echo.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository         userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock JwtTokenProvider       jwtTokenProvider;
    @Mock AppProperties          props;

    @InjectMocks AuthService authService;

    @Test
    void register_withNewEmail_returnsAuthResponse() {
        // given
        stubJwtProps();

        RegisterRequest request = new RegisterRequest("test@echo.com", "Test1234!", "Test User", "UTC", "tr");
        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hashed");
        User saved = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .displayName(request.displayName())
                .passwordHash("hashed")
                .timezone(request.timezone())
                .build();
        given(userRepository.save(any(User.class))).willReturn(saved);
        given(jwtTokenProvider.generateAccessToken(any(UUID.class), anyString(), anyInt())).willReturn("access-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        AuthResponse response = authService.register(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo("test@echo.com");
    }

    @Test
    void register_withExistingEmail_throwsEchoException() {
        // given
        RegisterRequest request = new RegisterRequest("existing@echo.com", "Test1234!", "User", "UTC", "tr");
        given(userRepository.existsByEmail(request.email())).willReturn(true);

        // when / then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        // given
        stubJwtProps();

        LoginRequest request = new LoginRequest("test@echo.com", "Test1234!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .passwordHash("hashed")
                .timezone("UTC")
                .displayName("Test")
                .build();
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "hashed")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(eq(user.getId()), anyString(), anyInt())).willReturn("access-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        AuthResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_withWrongPassword_throwsEchoException() {
        // given
        LoginRequest request = new LoginRequest("test@echo.com", "WrongPass!");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .passwordHash("hashed")
                .timezone("UTC")
                .displayName("Test")
                .build();
        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), "hashed")).willReturn(false);

        // when / then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePassword_withValidCurrentPassword_rotatesTokens() {
        stubJwtProps();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@echo.com")
                .passwordHash("hashed")
                .timezone("UTC")
                .displayName("Test")
                .passwordLoginEnabled(true)
                .build();

        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(passwordEncoder.matches("OldPassword123!", "hashed")).willReturn(true);
        given(passwordEncoder.matches("NewPassword123!", "hashed")).willReturn(false);
        given(passwordEncoder.encode("NewPassword123!")).willReturn("new-hashed");
        given(userRepository.save(any(User.class))).willReturn(user);
        given(jwtTokenProvider.generateAccessToken(eq(user.getId()), anyString(), anyInt())).willReturn("access-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.changePassword(
                user.getId(),
                new ChangePasswordRequest("OldPassword123!", "NewPassword123!")
        );

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
        assertThat(user.isEmailVerified()).isTrue();
        then(refreshTokenRepository).should().deleteByUserId(user.getId());
    }

    @Test
    void refresh_withValidStoredToken_rotatesTokenUsingLockedLookup() {
        stubJwtProps();

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@echo.com")
                .displayName("Test")
                .timezone("UTC")
                .build();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(hashForTest("refresh-token"))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        given(refreshTokenRepository.findByTokenHashForUpdate(stored.getTokenHash()))
                .willReturn(Optional.of(stored));
        given(jwtTokenProvider.generateAccessToken(eq(user.getId()), anyString(), anyInt())).willReturn("access-token");
        given(refreshTokenRepository.save(any(RefreshToken.class))).willAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh("refresh-token");

        assertThat(response.accessToken()).isEqualTo("access-token");
        then(refreshTokenRepository).should().findByTokenHashForUpdate(stored.getTokenHash());
        then(refreshTokenRepository).should().delete(stored);
    }

    @Test
    void refresh_whenTokenWasAlreadyConsumed_throwsUnauthorized() {
        given(refreshTokenRepository.findByTokenHashForUpdate(hashForTest("refresh-token")))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("refresh-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void logout_revokesTokenByHashWithoutLoadingEntity() {
        authService.logout("refresh-token");

        then(refreshTokenRepository).should().deleteByTokenHash(hashForTest("refresh-token"));
        then(refreshTokenRepository).should(never()).findByTokenHash(anyString());
    }

    private void stubJwtProps() {
        AppProperties.Jwt jwt = new AppProperties.Jwt();
        jwt.setAccessTokenExpirySeconds(900);
        jwt.setRefreshTokenExpirySeconds(2_592_000);
        given(props.getJwt()).willReturn(jwt);
    }

    private String hashForTest(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
