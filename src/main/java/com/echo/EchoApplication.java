package com.echo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.echo.config.AppProperties;

// @EnableAsync is on AsyncConfig — do not duplicate here
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class EchoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoApplication.class, args);
    }
}
