package com.echo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        AppProperties.Cors cors = appProperties.getCors();
        List<String> origins = cors.resolveOriginPatterns();
        if (origins == null || origins.isEmpty() || origins.contains("*")) {
            log.error("CORS origin patterns not configured — all cross-origin requests will be blocked");
            return; // fail-closed: no wildcard fallback
        }
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
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
        Path imagesPath = Paths.get(appProperties.getStorage().getLocalImagesPath())
                .toAbsolutePath()
                .normalize();
        registry.addResourceHandler("/uploads/images/**")
                .addResourceLocations(withTrailingSlash(imagesPath.toUri().toString()));
    }

    private static List<String> safeList(List<String> values, List<String> fallback) {
        return (values == null || values.isEmpty()) ? fallback : values;
    }

    private String withTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
