package com.zestflow.common.util;

import java.net.URI;

/**
 * 试验场业务 API 地址拼接
 */
public final class PlaygroundUrlHelper {

    private PlaygroundUrlHelper() {
    }

    /** 基址 + 相对路径，如 {@code http://host:8081} + {@code /api/orders/create} */
    public static String joinBaseUrl(String baseUrl, String relativePath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return relativePath;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = relativePath != null ? relativePath.trim() : "";
        if (path.isEmpty()) {
            return base;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    /** 完整 URL → 相对路径（含 query） */
    public static String toRelativePath(String fullUrl) {
        if (fullUrl == null || fullUrl.isBlank()) {
            return fullUrl;
        }
        String raw = fullUrl.trim().split("#")[0];
        if (!raw.contains("://")) {
            return raw.startsWith("/") ? raw : "/" + raw;
        }
        try {
            URI uri = URI.create(raw);
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getRawQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            int schemeEnd = raw.indexOf("://");
            if (schemeEnd < 0) {
                return raw;
            }
            int pathStart = raw.indexOf('/', schemeEnd + 3);
            return pathStart >= 0 ? raw.substring(pathStart) : "/";
        }
    }
}
