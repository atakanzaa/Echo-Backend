package com.echo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EchoApplicationTests {

    @Test
    void contextLoads() {
        // Spring context başarıyla yüklendi mi kontrol et
    }
}
