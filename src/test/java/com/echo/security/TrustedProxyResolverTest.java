package com.echo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedProxyResolverTest {

    private TrustedProxyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TrustedProxyResolver();
        resolver.load();
    }

    @Test
    void trustsCloudflareIpv4() {
        assertThat(resolver.isTrusted("173.245.48.1")).isTrue();
        assertThat(resolver.isTrusted("104.16.0.1")).isTrue();
    }

    @Test
    void trustsLoopback() {
        assertThat(resolver.isTrusted("127.0.0.1")).isTrue();
    }

    @Test
    void rejectsArbitraryPublicIp() {
        // A forged CF-Connecting-IP from a random internet host must be ignored.
        assertThat(resolver.isTrusted("8.8.8.8")).isFalse();
        assertThat(resolver.isTrusted("1.1.1.1")).isFalse();
    }

    @Test
    void rejectsMalformedAddress() {
        assertThat(resolver.isTrusted("not-an-ip")).isFalse();
        assertThat(resolver.isTrusted("")).isFalse();
        assertThat(resolver.isTrusted(null)).isFalse();
    }
}
