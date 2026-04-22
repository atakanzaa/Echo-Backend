package com.echo.service;

import com.echo.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleStoreKitServiceProfileTest {

    private AppleStoreKitService build(String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new AppleStoreKitService(
                new AppProperties(),
                new ObjectMapper(),
                env,
                new RestTemplate()
        );
    }

    @Test
    void initFailsInProdWhenNoPinnedTrustAnchorIsBundled() {
        // With no AppleRootCA-G3.cer on classpath, the prod profile must refuse
        // to start rather than silently fall back to self-anchored validation.
        AppleStoreKitService service = build("prod");
        assertThatThrownBy(service::initTrustAnchors)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trust anchors");
    }

    @Test
    void initSucceedsInNonProdWithoutPinnedAnchors() {
        AppleStoreKitService service = build("dev");
        // Must not throw — non-prod tolerates absent pinned roots.
        service.initTrustAnchors();
        assertThat(service).isNotNull();
    }
}
