package com.echo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StorageConfig {

    /**
     * AI API çağrılarında sonsuz beklemeyi önlemek için timeout yapılandırılmış RestTemplate.
     * Bağlantı zaman aşımı: 10 saniye (sabit).
     * Okuma zaman aşımı: app.ai.timeout-seconds (varsayılan 60s).
     */
    @Bean
    public RestTemplate restTemplate(AppProperties props) {
        int readTimeoutMs = props.getAi().getTimeoutSeconds() * 1_000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);    // 10s bağlantı zaman aşımı
        factory.setReadTimeout(readTimeoutMs); // AI_TIMEOUT'a göre okuma zaman aşımı
        return new RestTemplate(factory);
    }
}
