package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.token.RefreshToken;
import com.echo.domain.user.User;
import com.echo.dto.request.GoogleLoginRequest;
import com.echo.dto.request.LoginRequest;
import com.echo.dto.request.RegisterRequest;
import com.echo.dto.response.AuthResponse;
import com.echo.dto.response.UserResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.RefreshTokenRepository;
import com.echo.repository.UserRepository;
import com.echo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final GoogleIdentityService googleIdentityService;

    // generic error message prevents user enumeration attacks
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            // same generic message to prevent email enumeration
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        var user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .timezone(request.timezone() != null ? request.timezone() : "UTC")
                .preferredLanguage(request.language() != null ? request.language() : "tr")
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        GoogleIdentityService.GoogleIdentity identity = googleIdentityService.verifyIdToken(request.idToken());
        String email = normalizeEmail(identity.email());

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createUserFromGoogle(identity, request));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        if (!hasText(user.getDisplayName())) {
            user.setDisplayName(resolveDisplayName(identity.name(), email));
        }
        if (hasText(request.timezone())) {
            user.setTimezone(request.timezone());
        }
        if (hasText(request.language())) {
            user.setPreferredLanguage(request.language());
        }

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token expired, please login again");
        }

        // rotate: delete old token, issue new pair
        refreshTokenRepository.delete(stored);
        return buildAuthResponse(stored.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash)
                .ifPresent(refreshTokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String rawRefresh = UUID.randomUUID().toString();
        String hashedRefresh = hashToken(rawRefresh);

        long expirySeconds = props.getJwt().getRefreshTokenExpirySeconds();
        var refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashedRefresh)
                .expiresAt(OffsetDateTime.now().plusSeconds(expirySeconds))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefresh,
                props.getJwt().getAccessTokenExpirySeconds(),
                UserResponse.from(user)
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private User createUserFromGoogle(GoogleIdentityService.GoogleIdentity identity, GoogleLoginRequest request) {
        return userRepository.save(User.builder()
                .email(normalizeEmail(identity.email()))
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .displayName(resolveDisplayName(identity.name(), identity.email()))
                .timezone(hasText(request.timezone()) ? request.timezone() : "UTC")
                .preferredLanguage(hasText(request.language()) ? request.language() : "tr")
                .build());
    }

    private String resolveDisplayName(String candidate, String email) {
        if (hasText(candidate)) {
            return candidate.trim();
        }
        String local = email.split("@")[0];
        if (!hasText(local)) {
            return "Echo User";
        }
        return local.substring(0, 1).toUpperCase(Locale.ROOT) + local.substring(1);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
