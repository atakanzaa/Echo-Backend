package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.UnauthorizedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIdentityService {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final List<String> VALID_ISSUERS =
            List.of("accounts.google.com", "https://accounts.google.com");

    private final AppProperties appProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // JWKS rotates infrequently; 6-hour cache avoids per-request round trips.
    private final Cache<String, JWKSet> jwksCache = Caffeine.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .maximumSize(1)
            .build();

    public GoogleIdentity verifyIdToken(String idToken) {
        String clientId = appProperties.getGoogle().getClientId();
        if (!StringUtils.hasText(clientId)) {
            throw new ServiceUnavailableException("Google Sign-In is not configured");
        }

        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            JWKSet jwkSet = loadJwks();

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                    JWSAlgorithm.RS256,
                    new ImmutableJWKSet<>(jwkSet)
            ));

            JWTClaimsSet claims = processor.process(jwt, null);
            validateClaims(claims, clientId);

            String email   = claims.getStringClaim("email");
            String name    = claims.getStringClaim("name");
            String subject = claims.getSubject();

            return new GoogleIdentity(subject, email != null ? email.trim().toLowerCase() : "", name != null ? name.trim() : "");

        } catch (UnauthorizedException | ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Google ID token verification failed: {}", ex.getMessage());
            throw new UnauthorizedException("Invalid Google identity token");
        }
    }

    private void validateClaims(JWTClaimsSet claims, String expectedClientId) throws Exception {
        String issuer = claims.getIssuer();
        if (!VALID_ISSUERS.contains(issuer)) {
            throw new UnauthorizedException("Invalid Google identity token");
        }

        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(expectedClientId)) {
            throw new UnauthorizedException("Invalid Google identity token");
        }

        if (claims.getExpirationTime() == null
                || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
            throw new UnauthorizedException("Google identity token expired");
        }

        Object emailVerified = claims.getClaim("email_verified");
        String email = claims.getStringClaim("email");
        boolean verified = Boolean.TRUE.equals(emailVerified)
                || "true".equalsIgnoreCase(String.valueOf(emailVerified));
        if (!StringUtils.hasText(email) || !verified) {
            throw new UnauthorizedException("Google account email is not verified");
        }
    }

    private JWKSet loadJwks() {
        return jwksCache.get("google", ignored -> fetchJwks());
    }

    private JWKSet fetchJwks() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_JWKS_URL))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ServiceUnavailableException("Failed to fetch Google JWKS: HTTP " + response.statusCode());
            }
            return JWKSet.parse(response.body());
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Google JWKS fetch failed: " + ex.getMessage());
        }
    }

    public record GoogleIdentity(String subject, String email, String name) {}
}
