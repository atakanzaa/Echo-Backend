package com.echo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageServiceMimeTest {

    @Test
    void detectsJpeg() {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
        assertThat(StorageService.detectImageMime(head)).isEqualTo("image/jpeg");
    }

    @Test
    void detectsPng() {
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertThat(StorageService.detectImageMime(head)).isEqualTo("image/png");
    }

    @Test
    void detectsWebp() {
        byte[] head = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(StorageService.detectImageMime(head)).isEqualTo("image/webp");
    }

    @Test
    void rejectsHtmlMasqueradingAsJpeg() {
        // An attacker uploading <script>... labelled "image/jpeg" must be caught.
        byte[] html = "<html><body><script>alert(1)</script>".getBytes();
        assertThat(StorageService.detectImageMime(html)).isNull();
    }

    @Test
    void rejectsSvg() {
        // SVG is XML; it lacks a known image magic prefix so must not be accepted.
        byte[] svg = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
        assertThat(StorageService.detectImageMime(svg)).isNull();
    }

    @Test
    void handlesShortInput() {
        assertThat(StorageService.detectImageMime(new byte[]{1, 2})).isNull();
        assertThat(StorageService.detectImageMime(null)).isNull();
    }
}
