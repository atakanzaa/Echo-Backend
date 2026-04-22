package com.echo.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityFilterRegistrationTest {

    @Autowired
    @Qualifier("jwtAuthenticationFilterRegistration")
    FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration;

    @Autowired
    @Qualifier("rateLimitFilterRegistration")
    FilterRegistrationBean<RateLimitFilter> rateLimitRegistration;

    @Test
    void securityChainFiltersAreNotServletAutoRegistered() {
        assertThat(jwtRegistration.isEnabled()).isFalse();
        assertThat(rateLimitRegistration.isEnabled()).isFalse();
    }
}
