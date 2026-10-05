package com.ruiding.erp.service.packageunit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RdDigitalLinkService {

    private final String baseUrl;

    public RdDigitalLinkService(
            @Value("${rd.erp.digital-link-base-url:https://id.tjldsic.com/pkg}") String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public String build(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            throw new IllegalArgumentException("Package code must not be blank");
        }
        return baseUrl + "/" + packageCode.trim();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("Digital Link base URL must use HTTP or HTTPS");
        }
        return normalized;
    }
}
