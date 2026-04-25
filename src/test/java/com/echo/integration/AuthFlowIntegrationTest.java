package com.echo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end happy path: register → fetch profile with the issued access token.
 * Uses a real Postgres via Testcontainers so Flyway migrations execute identically to prod.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("it")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-must-be-at-least-32-chars-long-aaaa",
        "app.ai.fallback-provider="
})
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void disableTracingExporter(DynamicPropertyRegistry registry) {
        registry.add("management.otlp.tracing.endpoint", () -> "");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    @Test
    void registerThenAccessProtectedEndpoint() throws Exception {
        String registerBody = """
                {
                  "email": "e2e-user@example.com",
                  "password": "Password123!",
                  "display_name": "E2E User",
                  "timezone": "UTC",
                  "language": "en"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").exists())
                .andReturn();

        JsonNode body = mapper.readTree(registerResult.getResponse().getContentAsString());
        String accessToken = body.path("access_token").asText(null);
        if (accessToken == null) {
            // some payloads use camelCase; tolerate either
            accessToken = body.path("accessToken").asText();
        }
        assertThat(accessToken).isNotBlank();

        // The /privacy/consent endpoint is authenticated and requires no extra setup.
        mockMvc.perform(get("/api/v1/privacy/consent")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
