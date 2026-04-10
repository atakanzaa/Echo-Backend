package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GoogleIdentityService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public GoogleIdentity verifyIdToken(String idToken) {
        String configuredClientId = appProperties.getGoogle().getClientId();
        if (!StringUtils.hasText(configuredClientId)) {
            throw new ServiceUnavailableException("Google Sign-In is not configured");
        }

        JsonNode payload = fetchTokenInfo(idToken);
        validateIssuer(payload.path("iss").asText(""));
        validateAudience(payload.path("aud").asText(""), configuredClientId);
        validateExpiry(payload.path("exp").asText(""));
        validateEmail(payload);

        String email = payload.path("email").asText("").trim().toLowerCase();
        String name = payload.path("name").asText("").trim();
        String subject = payload.path("sub").asText("").trim();

        return new GoogleIdentity(subject, email, name);
    }

    private JsonNode fetchTokenInfo(String idToken) {
        String url = appProperties.getGoogle().getTokenInfoUrl();
        URI uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("id_token", idToken)
                .build(true)
                .toUri();

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ServiceUnavailableException("Google token verification URL is invalid");
        }

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new UnauthorizedException("Invalid Google identity token");
            }
            return objectMapper.readTree(response.body());
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceUnavailableException("Google token verification failed");
        }
    }

    private void validateIssuer(String issuer) {
        boolean validIssuer = "accounts.google.com".equals(issuer)
                || "https://accounts.google.com".equals(issuer);
        if (!validIssuer) {
            throw new UnauthorizedException("Invalid Google identity token");
        }
    }

    private void validateAudience(String audience, String expectedClientId) {
        if (!expectedClientId.equals(audience)) {
            throw new UnauthorizedException("Invalid Google identity token");
        }
    }

    private void validateExpiry(String expClaim) {
        if (!StringUtils.hasText(expClaim)) {
            throw new UnauthorizedException("Invalid Google identity token");
        }
        try {
            long exp = Long.parseLong(expClaim);
            if (exp <= Instant.now().getEpochSecond()) {
                throw new UnauthorizedException("Google identity token expired");
            }
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException("Invalid Google identity token");
        }
    }

    private void validateEmail(JsonNode payload) {
        String email = payload.path("email").asText("");
        boolean emailVerified = payload.path("email_verified").asBoolean(false);
        if (!StringUtils.hasText(email) || !emailVerified) {
            throw new UnauthorizedException("Google account email is not verified");
        }
    }

    public record GoogleIdentity(String subject, String email, String name) {}
}
