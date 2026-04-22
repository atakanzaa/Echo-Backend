package com.echo.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Checks whether a remote address belongs to a trusted proxy (Cloudflare + private ranges). */
@Slf4j
@Component
public class TrustedProxyResolver {

    private static final String CIDR_RESOURCE = "cloudflare-ips.txt";

    private final List<CidrRange> ranges = new ArrayList<>();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(CIDR_RESOURCE);
        if (!resource.exists()) {
            log.warn("{} not found on classpath; CF-Connecting-IP will be ignored", CIDR_RESOURCE);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                try {
                    ranges.add(CidrRange.parse(trimmed));
                } catch (Exception ex) {
                    log.warn("Skipping invalid CIDR entry '{}': {}", trimmed, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to read {}: {}", CIDR_RESOURCE, ex.getMessage());
        }
        log.info("Trusted proxy ranges loaded: {}", ranges.size());
    }

    public boolean isTrusted(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        InetAddress address;
        try {
            address = InetAddress.getByName(remoteAddr);
        } catch (Exception ex) {
            return false;
        }
        byte[] bytes = address.getAddress();
        for (CidrRange range : ranges) {
            if (range.contains(bytes)) return true;
        }
        return false;
    }

    private record CidrRange(BigInteger networkBits, BigInteger mask, int addressLength) {
        static CidrRange parse(String cidr) throws Exception {
            int slash = cidr.indexOf('/');
            if (slash < 0) throw new IllegalArgumentException("Missing /prefix");
            String host = cidr.substring(0, slash);
            int prefix = Integer.parseInt(cidr.substring(slash + 1));
            byte[] bytes = InetAddress.getByName(host).getAddress();
            int bits = bytes.length * 8;
            if (prefix < 0 || prefix > bits) throw new IllegalArgumentException("Invalid prefix");
            BigInteger mask = BigInteger.ONE.shiftLeft(bits)
                    .subtract(BigInteger.ONE)
                    .shiftLeft(bits - prefix)
                    .and(BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE));
            BigInteger net = new BigInteger(1, bytes).and(mask);
            return new CidrRange(net, mask, bytes.length);
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != addressLength) return false;
            BigInteger value = new BigInteger(1, candidate).and(mask);
            return value.equals(networkBits);
        }
    }
}
