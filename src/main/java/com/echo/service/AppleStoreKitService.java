package com.echo.service;

import com.echo.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.echo.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AppleStoreKitService {

    private static final String PROD_JWKS_URL =
            "https://api.storekit.itunes.apple.com/inApps/v1/notifications/jwsPublicKeys";
    private static final String SANDBOX_JWKS_URL =
            "https://api.storekit-sandbox.itunes.apple.com/inApps/v1/notifications/jwsPublicKeys";

    // Pinned Apple root CAs loaded from classpath:certs/ at startup.
    private static final List<String> PINNED_ROOT_RESOURCES = List.of(
            "certs/AppleRootCA-G3.cer",
            "certs/AppleIncRootCertificate.cer"
    );

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private final RestTemplate restTemplate;
    private final Cache<String, JWKSet> jwksCache = Caffeine.newBuilder()
            .expireAfterWrite(6, TimeUnit.HOURS)
            .maximumSize(4)
            .build();

    private Set<TrustAnchor> pinnedTrustAnchors = Set.of();
    private boolean prodProfile;

    public AppleStoreKitService(AppProperties appProperties,
                                ObjectMapper objectMapper,
                                Environment environment,
                                RestTemplate restTemplate) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void initTrustAnchors() {
        this.prodProfile = environment.matchesProfiles("prod");
        Set<TrustAnchor> anchors = new HashSet<>();
        CertificateFactory factory;
        try {
            factory = CertificateFactory.getInstance("X.509");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to obtain X.509 certificate factory", ex);
        }
        for (String resourcePath : PINNED_ROOT_RESOURCES) {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) continue;
            try (InputStream in = resource.getInputStream()) {
                X509Certificate cert = (X509Certificate) factory.generateCertificate(in);
                anchors.add(new TrustAnchor(cert, null));
                log.info("Apple root CA pinned: {} subject={}", resourcePath, cert.getSubjectX500Principal());
            } catch (Exception ex) {
                log.warn("Failed to load pinned Apple root {}: {}", resourcePath, ex.getMessage());
            }
        }
        this.pinnedTrustAnchors = Set.copyOf(anchors);
        if (prodProfile && pinnedTrustAnchors.isEmpty()) {
            throw new IllegalStateException(
                    "Apple StoreKit trust anchors missing. Place AppleRootCA-G3.cer under " +
                    "src/main/resources/certs/ for the prod profile.");
        }
        if (pinnedTrustAnchors.isEmpty()) {
            log.warn("Apple StoreKit trust anchors not pinned (non-prod profile). " +
                     "Cert chain validation will be best-effort only.");
        }
    }

    public AppleTransactionPayload verifyAndDecodeTransaction(String signedTransaction) {
        String compactJws = normalizeSignedValue(signedTransaction, Set.of("signedTransaction", "signed_transaction"));
        SignedJWT jwt = parseAndVerify(compactJws);

        JWTClaimsSet claims = getClaims(jwt);
        Map<String, Object> rawClaims = claims.getClaims();

        AppleTransactionPayload payload = new AppleTransactionPayload(
                asString(rawClaims.get("transactionId")),
                asString(rawClaims.get("originalTransactionId")),
                asString(rawClaims.get("productId")),
                toOffsetDateTime(rawClaims.get("purchaseDate")),
                toOffsetDateTime(rawClaims.get("expiresDate")),
                toOffsetDateTime(rawClaims.get("gracePeriodExpiresDate")),
                coalesceEnvironment(asString(rawClaims.get("environment"))),
                asString(rawClaims.get("bundleId")),
                rawClaims
        );

        validateBundle(payload.bundleId());
        return payload;
    }

    public AppleNotificationPayload decodeNotification(String signedPayload) {
        String compactJws = normalizeSignedValue(signedPayload, Set.of("signedPayload", "signed_payload"));
        SignedJWT notificationJwt = parseAndVerify(compactJws);
        Map<String, Object> payload = getClaims(notificationJwt).getClaims();

        String notificationType = asString(payload.get("notificationType"));
        String subtype = asString(payload.get("subtype"));

        Map<String, Object> data = asMap(payload.get("data"));
        String signedTransactionInfo = data == null ? null : asString(data.get("signedTransactionInfo"));

        AppleTransactionPayload transactionPayload = null;
        if (StringUtils.hasText(signedTransactionInfo)) {
            transactionPayload = verifyAndDecodeTransaction(signedTransactionInfo);
        }

        return new AppleNotificationPayload(notificationType, subtype, transactionPayload, payload);
    }

    private SignedJWT parseAndVerify(String compactJws) {
        if (!StringUtils.hasText(compactJws)) {
            throw new IllegalArgumentException("Apple signed payload is empty");
        }

        try {
            SignedJWT jwt = SignedJWT.parse(compactJws);
            verifyCertificateChain(jwt.getHeader());
            verifySignature(jwt);
            return jwt;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to validate Apple JWS payload", ex);
        }
    }

    private void verifySignature(SignedJWT jwt) throws Exception {
        JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
        if (algorithm == null || JWSAlgorithm.NONE.equals(algorithm)) {
            throw new IllegalArgumentException("Unsupported Apple JWS algorithm");
        }

        boolean verified = verifyWithEnvironment(jwt, "Production");
        if (!verified && !prodProfile) {
            verified = verifyWithEnvironment(jwt, "Sandbox");
        }
        if (!verified) {
            throw new IllegalArgumentException("Apple JWS signature verification failed");
        }
    }

    private boolean verifyWithEnvironment(SignedJWT jwt, String environment) throws Exception {
        JWKSet jwkSet = getJwkSet(environment);
        String keyId = jwt.getHeader().getKeyID();

        for (JWK jwk : jwkSet.getKeys()) {
            if (StringUtils.hasText(keyId) && !keyId.equals(jwk.getKeyID())) {
                continue;
            }

            JWSVerifier verifier = buildVerifier(jwk);
            if (verifier != null && jwt.verify(verifier)) {
                return true;
            }
        }

        return false;
    }

    private JWSVerifier buildVerifier(JWK jwk) throws Exception {
        if (jwk instanceof RSAKey rsaKey) {
            return new RSASSAVerifier(rsaKey.toRSAPublicKey());
        }
        if (jwk instanceof ECKey ecKey) {
            return new ECDSAVerifier(ecKey.toECPublicKey());
        }
        return null;
    }

    @CircuitBreaker(name = "apple-storekit", fallbackMethod = "getJwkSetFallback")
    JWKSet getJwkSet(String environment) {
        JWKSet cached = jwksCache.getIfPresent(environment);
        if (cached != null) {
            return cached;
        }

        String url = "sandbox".equalsIgnoreCase(environment) ? SANDBOX_JWKS_URL : PROD_JWKS_URL;
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (!StringUtils.hasText(body)) {
                throw new IllegalStateException("Empty JWKS response from Apple");
            }
            JWKSet jwkSet = JWKSet.parse(body);
            jwksCache.put(environment, jwkSet);
            return jwkSet;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to fetch Apple JWKS: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Apple JWKS payload", ex);
        }
    }

    JWKSet getJwkSetFallback(String environment, Exception ex) {
        log.error("Apple JWKS fetch failed (circuit open): env={}, error={}", environment, ex.getMessage());
        throw new ServiceUnavailableException("Apple StoreKit is temporarily unavailable");
    }

    private void verifyCertificateChain(JWSHeader header) {
        List<Base64> certChain = header.getX509CertChain();
        if (certChain == null || certChain.isEmpty()) {
            if (prodProfile) {
                throw new IllegalArgumentException("Apple JWS missing x5c certificate chain");
            }
            return;
        }

        if (pinnedTrustAnchors.isEmpty()) {
            if (prodProfile) {
                throw new IllegalStateException("Apple trust anchors not configured in prod profile");
            }
        }

        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> certificates = certChain.stream()
                    .map(base64 -> decodeCertificate(factory, base64))
                    .toList();

            for (X509Certificate certificate : certificates) {
                certificate.checkValidity();
            }

            if (certificates.size() < 2) {
                throw new IllegalArgumentException("Apple certificate chain too short");
            }

            List<X509Certificate> leafAndIntermediates = certificates.subList(0, certificates.size() - 1);
            Set<TrustAnchor> anchors = pinnedTrustAnchors.isEmpty()
                    ? Set.of(new TrustAnchor(certificates.get(certificates.size() - 1), null))
                    : pinnedTrustAnchors;

            CertPath certPath = factory.generateCertPath(leafAndIntermediates);
            PKIXParameters params = new PKIXParameters(anchors);
            params.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, params);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Apple certificate chain", ex);
        }
    }

    private X509Certificate decodeCertificate(CertificateFactory factory, Base64 base64) {
        try {
            byte[] encoded = base64.decode();
            return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(encoded));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decode Apple certificate", ex);
        }
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse Apple JWT claims", ex);
        }
    }

    private String normalizeSignedValue(String rawValue, Set<String> jsonKeys) {
        if (!StringUtils.hasText(rawValue)) {
            return rawValue;
        }

        String trimmed = rawValue.trim();
        try {
            if (trimmed.startsWith("{")) {
                Map<String, Object> body = objectMapper.readValue(trimmed, new TypeReference<>() {});
                for (String key : jsonKeys) {
                    String candidate = asString(body.get(key));
                    if (StringUtils.hasText(candidate)) {
                        return candidate;
                    }
                }
            }

            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                return objectMapper.readValue(trimmed, String.class);
            }
        } catch (Exception ex) {
            log.debug("Apple payload normalization fallback: {}", ex.getMessage());
        }

        return trimmed;
    }

    private void validateBundle(String bundleId) {
        String configuredBundleId = appProperties.getApple().getBundleId();
        if (StringUtils.hasText(configuredBundleId)
                && StringUtils.hasText(bundleId)
                && !configuredBundleId.equals(bundleId)) {
            throw new IllegalArgumentException("Apple bundleId mismatch");
        }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneOffset.UTC);
        }

        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }

            try {
                long epoch = Long.parseLong(trimmed);
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            } catch (NumberFormatException ignored) {
                return OffsetDateTime.parse(trimmed);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        return Collections.emptyMap();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String coalesceEnvironment(String environment) {
        if (!StringUtils.hasText(environment)) {
            return "Production";
        }
        String normalized = environment.trim().toLowerCase(Locale.ROOT);
        return "sandbox".equals(normalized) ? "Sandbox" : "Production";
    }

    public record AppleTransactionPayload(
            String transactionId,
            String originalTransactionId,
            String productId,
            OffsetDateTime purchaseDate,
            OffsetDateTime expiresDate,
            OffsetDateTime gracePeriodExpiresDate,
            String environment,
            String bundleId,
            Map<String, Object> rawClaims
    ) {
    }

    public record AppleNotificationPayload(
            String notificationType,
            String subtype,
            AppleTransactionPayload transaction,
            Map<String, Object> rawPayload
    ) {
    }
}
