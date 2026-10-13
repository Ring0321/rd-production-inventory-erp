package com.ruiding.erp.service.packageunit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Service
public class RdDigitalLinkService {

    private final String baseUrl;

    public RdDigitalLinkService(
            @Value("${rd.erp.digital-link-base-url:http://localhost:9000/pkg}") String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public String build(String packageCode) {
        String link = baseUrl + "/" + checkedCode(packageCode);
        if (link.length() > 128) throw new IllegalArgumentException("Digital Link exceeds the package identity storage limit");
        return link;
    }

    public String representation(String packageCode, String resource) {
        if (!Set.of("passport", "document", "links").contains(resource)) {
            throw new IllegalArgumentException("Unsupported package representation");
        }
        return build(packageCode) + "?resource=" + resource;
    }

    public static String checkedIdentity(String packageCode, String value) {
        String code = checkedCode(packageCode);
        URI uri = checkedUri(value);
        if (!uri.getPath().endsWith("/pkg/" + code)) {
            throw new IllegalArgumentException("Stored link does not match the package identity");
        }
        return value.trim();
    }

    public static String checkedCode(String value) {
        String code = value == null ? "" : value.trim();
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid package identity");
        }
        return code;
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = checkedUri(normalized);
        if (!uri.getRawPath().matches("/(?:[A-Za-z0-9_-]+/)*pkg")) {
            throw new IllegalArgumentException("Digital Link base path must use supported path segments and end with /pkg");
        }
        return normalized;
    }

    private static URI checkedUri(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim()).parseServerAuthority();
            if (!Set.of("http", "https").contains(String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535
                    || uri.getPath() == null || Arrays.stream(uri.getPath().split("/")).anyMatch(p -> p.equals(".") || p.equals(".."))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception e) { throw new IllegalArgumentException("Invalid public Digital Link address"); }
    }
}
