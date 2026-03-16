package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.token.RefreshToken;
import com.echo.domain.user.User;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider      jwtTokenProvider;
    private final PasswordEncoder       passwordEncoder;
    private final AppProperties         props;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Bu e-posta adresi zaten kullanımda");
        }

        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .timezone(request.timezone() != null ? request.timezone() : "UTC")
                .build();

        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("E-posta veya şifre hatalı"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("E-posta veya şifre hatalı");
        }
        if (!user.isActive()) {
            throw new UnauthorizedException("Hesabınız devre dışı bırakılmış");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Geçersiz refresh token"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new UnauthorizedException("Refresh token süresi dolmuş, lütfen yeniden giriş yapın");
        }

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
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String rawRefresh   = UUID.randomUUID().toString();
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
            throw new IllegalStateException("SHA-256 bulunamadı", e);
        }
    }
}
