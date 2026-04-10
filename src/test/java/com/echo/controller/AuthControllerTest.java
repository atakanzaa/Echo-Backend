package com.echo.controller;

import com.echo.dto.request.LoginRequest;
import com.echo.dto.request.RefreshTokenRequest;
import com.echo.dto.request.RegisterRequest;
import com.echo.dto.response.AuthResponse;
import com.echo.dto.response.UserResponse;
import com.echo.security.JwtAuthenticationFilter;
import com.echo.security.RateLimitFilter;
import com.echo.security.UserDetailsServiceImpl;
import com.echo.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean RateLimitFilter rateLimitFilter;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private AuthResponse stubAuthResponse() {
        UserResponse user = new UserResponse(
                UUID.randomUUID(), "test@echo.com", "Test User", "UTC",
                0, 0, 0, BigDecimal.ZERO, "tr"
        );
        return new AuthResponse("access-token-123", "refresh-token-456", 900, user);
    }

    @Test
    void register_withValidBody_returns201() throws Exception {
        RegisterRequest req = new RegisterRequest("test@echo.com", "Test1234!", "Test User", "UTC", "tr");
        given(authService.register(any())).willReturn(stubAuthResponse());

                mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access-token-123"))
                .andExpect(jsonPath("$.user.email").value("test@echo.com"));
    }

    @Test
    void login_withValidBody_returns200() throws Exception {
        LoginRequest req = new LoginRequest("test@echo.com", "Test1234!");
        given(authService.login(any())).willReturn(stubAuthResponse());

                mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token-123"));
    }

    @Test
    @WithMockUser
    void logout_withValidBody_returns200() throws Exception {
        RefreshTokenRequest req = new RefreshTokenRequest("refresh-token-456");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }
}
