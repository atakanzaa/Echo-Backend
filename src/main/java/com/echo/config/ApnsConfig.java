package com.echo.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.echo.domain.notification.ApnsEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.Locale;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.apns.enabled", havingValue = "true")
public class ApnsConfig {

    private final AppProperties props;
    private ApnsClient apnsClient;

    @Bean
    public ApnsClient apnsClient() throws Exception {
        AppProperties.Apns apns = props.getApns();
        ApnsEnvironment environment = ApnsEnvironment.fromClient(apns.getEnvironment());
        String host = environment == ApnsEnvironment.SANDBOX
                ? ApnsClientBuilder.DEVELOPMENT_APNS_HOST
                : ApnsClientBuilder.PRODUCTION_APNS_HOST;

        File keyFile = new File(apns.getKeyPath());
        if (!keyFile.exists() || !keyFile.canRead()) {
            throw new IllegalStateException(
                    "APNS_KEY_PATH is not readable: " + apns.getKeyPath());
        }

        ApnsSigningKey signingKey = ApnsSigningKey.loadFromPkcs8File(
                keyFile, apns.getTeamId(), apns.getKeyId());

        this.apnsClient = new ApnsClientBuilder()
                .setApnsServer(host)
                .setSigningKey(signingKey)
                .setConnectionTimeout(java.time.Duration.ofSeconds(apns.getConnectionTimeoutSeconds()))
                .build();

        log.info("APNs client initialized environment={} host={} topic={}",
                environment, host, apns.getTopic());
        return this.apnsClient;
    }

    @Bean
    public ApnsEnvironment configuredApnsEnvironment() {
        String raw = props.getApns().getEnvironment();
        return ApnsEnvironment.fromClient(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
    }

    @PreDestroy
    public void shutdown() {
        if (apnsClient != null) {
            apnsClient.close();
        }
    }
}
