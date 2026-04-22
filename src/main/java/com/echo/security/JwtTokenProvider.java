package com.echo.security;

import com.echo.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String ISSUER        = "echo-backend";
    private static final String AUDIENCE      = "echo-ios";
    private static final String CLAIM_VERSION = "tv"; // token version

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtTokenProvider(AppProperties props) {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 32 characters. Generate with: openssl rand -base64 64");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = props.getJwt().getAccessTokenExpirySeconds() * 1000L;
    }

    public String generateAccessToken(UUID userId, String email, int tokenVersion) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    public int getTokenVersionFromToken(String token) {
        Claims claims = validateAndParseClaims(token);
        Object tv = claims.get(CLAIM_VERSION);
        if (tv instanceof Number n) return n.intValue();
        return 0;
    }

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndParseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        return UUID.fromString(validateAndParseClaims(token).getSubject());
    }
}
