package com.echo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        AppProperties.Cors cors = appProperties.getCors();
        registry.addMapping("/api/**")
                .allowedOriginPatterns(safeList(cors.resolveOriginPatterns(), List.of("*"))
                        .toArray(String[]::new))
                .allowedMethods(safeList(
                        cors.getAllowedMethods(),
                        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                ).toArray(String[]::new))
                .allowedHeaders(safeList(
                        cors.getAllowedHeaders(),
                        List.of("Content-Type", "Authorization", "Accept", "X-Request-ID")
                ).toArray(String[]::new))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(cors.getMaxAgeSeconds());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + System.getProperty("user.home") + "/echo-uploads/");
    }

    private List<String> safeList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values;
    }
}
