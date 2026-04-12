package com.echo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import com.echo.service.PasswordResetService;
import com.echo.service.SubscriptionService;

@SpringBootTest
@ActiveProfiles("test")
class EchoApplicationTests {

    @MockBean
    PasswordResetService passwordResetService;

    @MockBean
    SubscriptionService subscriptionService;

    @Test
    void contextLoads() {
        // Spring context başarıyla yüklendi mi kontrol et
    }
}
