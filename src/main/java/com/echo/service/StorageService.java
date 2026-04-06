package com.echo.service;

import com.echo.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final AppProperties props;
    // Only present when app.storage.type=s3; empty for local mode
    private final Optional<S3Client> s3Client;

    @PostConstruct
    void logStorageMode() {
        String type = props.getStorage().getType();
        if ("s3".equals(type)) {
            log.info("Storage mode: R2/S3 — bucket={} s3ClientPresent={}",
                    props.getStorage().getImagesBucket(), s3Client.isPresent());
        } else {
            log.info("Storage mode: local — path={}", props.getStorage().getLocalImagesPath());
        }
    }

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    // ── Image Storage ─────────────────────────────────────────────────────────

    /**
     * Validates, stores a community post image, and returns its public URL.
     * Allowed types: JPEG, PNG, WEBP. Max size: 10 MB.
     */
    public String uploadImage(MultipartFile file) {
        validateImage(file);
        return switch (props.getStorage().getType()) {
            case "local" -> saveImageLocally(file);
            case "s3"    -> saveImageToS3(file);
            default      -> throw new IllegalStateException("Unknown storage type: " + props.getStorage().getType());
        };
    }

    /**
     * Deletes a community post image by its stored URL.
     */
    public void deleteImage(String imageUrl) {
        if (imageUrl == null) return;
        if ("local".equals(props.getStorage().getType())) {
            deleteImageLocally(imageUrl);
        } else if ("s3".equals(props.getStorage().getType())) {
            deleteImageFromS3(imageUrl);
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds 10 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid image type. Allowed: JPEG, PNG, WEBP");
        }
    }

    // ── Local Image ───────────────────────────────────────────────────────────

    private String saveImageLocally(MultipartFile file) {
        try {
            Path dir = Paths.get(props.getStorage().getLocalImagesPath());
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + extensionForMime(file.getContentType());
            Files.write(dir.resolve(filename), file.getBytes());
            log.debug("Image saved locally: {}", dir.resolve(filename));
            return "/uploads/images/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Image could not be saved", e);
        }
    }

    private void deleteImageLocally(String imageUrl) {
        try {
            // imageUrl is like /uploads/images/<uuid>.jpg
            String filename = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
            Path path = Paths.get(props.getStorage().getLocalImagesPath()).resolve(filename);
            Files.deleteIfExists(path);
            log.debug("Local image deleted: {}", path);
        } catch (IOException e) {
            log.warn("Could not delete local image: {}", imageUrl, e);
        }
    }

    // ── S3 / R2 Image ─────────────────────────────────────────────────────────

    private String saveImageToS3(MultipartFile file) {
        try {
            String key = "community-images/" + UUID.randomUUID() + "." + extensionForMime(file.getContentType());
            s3Client.orElseThrow(() -> new IllegalStateException("S3Client not configured"))
                    .putObject(
                            PutObjectRequest.builder()
                                    .bucket(props.getStorage().getImagesBucket())
                                    .key(key)
                                    .contentType(file.getContentType())
                                    .contentLength(file.getSize())
                                    .build(),
                            RequestBody.fromBytes(file.getBytes())
                    );
            String url = props.getStorage().getImagesPublicBaseUrl() + "/" + key;
            log.debug("Image uploaded to R2: {}", url);
            return url;
        } catch (IOException e) {
            throw new RuntimeException("Image upload to R2 failed", e);
        }
    }

    private void deleteImageFromS3(String imageUrl) {
        String baseUrl = props.getStorage().getImagesPublicBaseUrl();
        if (baseUrl.isEmpty() || !imageUrl.startsWith(baseUrl)) return;
        // Strip base URL and leading slash to get the S3 object key
        String key = imageUrl.substring(baseUrl.length()).replaceFirst("^/", "");
        s3Client.ifPresent(client -> {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getStorage().getImagesBucket())
                    .key(key)
                    .build());
            log.debug("R2 image deleted: {}", key);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extensionForMime(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/webp" -> "webp";
            default           -> "jpg";
        };
    }
}
