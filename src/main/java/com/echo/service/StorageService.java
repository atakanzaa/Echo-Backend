package com.echo.service;

import com.echo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final AppProperties props;

    /**
     * Ses dosyasını saklar ve URL döner.
     */
    public String save(byte[] audioBytes, String originalFilename) {
        String storageType = props.getStorage().getType();
        return switch (storageType) {
            case "local" -> saveLocally(audioBytes, originalFilename);
            case "s3"    -> saveToS3(audioBytes, originalFilename);
            default      -> throw new IllegalStateException("Bilinmeyen storage tipi: " + storageType);
        };
    }

    /**
     * Ses dosyasını URL'den siler.
     */
    public void delete(String audioUrl) {
        if (audioUrl == null) return;
        String storageType = props.getStorage().getType();
        if ("local".equals(storageType)) {
            deleteLocally(audioUrl);
        }
        // S3 implementasyonu Phase 7'de eklenecek
    }

    // ── Local Storage ────────────────────────────────────────────────────────

    private String saveLocally(byte[] audioBytes, String originalFilename) {
        try {
            Path dir = Paths.get(props.getStorage().getLocalPath());
            Files.createDirectories(dir);

            String extension = getExtension(originalFilename);
            String filename  = UUID.randomUUID() + "." + extension;
            Path   filePath  = dir.resolve(filename);

            Files.write(filePath, audioBytes);
            log.debug("Ses dosyası kaydedildi: {}", filePath);

            return "local://" + filePath.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Ses dosyası kaydedilemedi", e);
        }
    }

    private void deleteLocally(String audioUrl) {
        try {
            if (audioUrl.startsWith("local://")) {
                Path path = Paths.get(audioUrl.substring(8));
                Files.deleteIfExists(path);
                log.debug("Ses dosyası silindi: {}", path);
            }
        } catch (IOException e) {
            log.warn("Ses dosyası silinemedi: {}", audioUrl, e);
        }
    }

    // ── S3 Storage (Phase 7) ─────────────────────────────────────────────────

    private String saveToS3(byte[] audioBytes, String originalFilename) {
        // TODO: Phase 7 — AWS S3 entegrasyonu
        throw new UnsupportedOperationException("S3 storage Phase 7'de implement edilecek");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "m4a";
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
