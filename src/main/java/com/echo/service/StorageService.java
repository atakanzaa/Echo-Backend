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
        String detectedMime = validateImageAndDetectMime(file);
        return switch (props.getStorage().getType()) {
            case "local" -> saveImageLocally(file, detectedMime);
            case "s3"    -> saveImageToS3(file, detectedMime);
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

    // Validates size and detects MIME from magic bytes — ignores client-supplied Content-Type.
    private String validateImageAndDetectMime(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image exceeds 10 MB limit");
        }
        try {
            String detected = detectImageMime(readHead(file));
            if (detected == null || !ALLOWED_IMAGE_TYPES.contains(detected)) {
                throw new IllegalArgumentException("Invalid image type. Allowed: JPEG, PNG, WEBP");
            }
            return detected;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read image", e);
        }
    }

    private byte[] readHead(MultipartFile file) throws IOException {
        try (var in = file.getInputStream()) {
            byte[] buf = new byte[16];
            int read = 0;
            while (read < buf.length) {
                int n = in.read(buf, read, buf.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read < buf.length) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buf, 0, trimmed, 0, read);
                return trimmed;
            }
            return buf;
        }
    }

    static String detectImageMime(byte[] head) {
        if (head == null || head.length < 4) return null;
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (head.length >= 8
                && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    // ── Local Image ───────────────────────────────────────────────────────────

    private String saveImageLocally(MultipartFile file, String mimeType) {
        try {
            Path dir = Paths.get(props.getStorage().getLocalImagesPath());
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "." + extensionForMime(mimeType);
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

    private String saveImageToS3(MultipartFile file, String mimeType) {
        try {
            String key = "community-images/" + UUID.randomUUID() + "." + extensionForMime(mimeType);
            s3Client.orElseThrow(() -> new IllegalStateException("S3Client not configured"))
                    .putObject(
                            PutObjectRequest.builder()
                                    .bucket(props.getStorage().getImagesBucket())
                                    .key(key)
                                    .contentType(mimeType)
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
