package com.zestflow.admin.client;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分页查询参数解析 — 多 Executor fan-out 合并时复用（对标 ES 协调节点本地分页）。
 */
public final class PagedQueryParser {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int DEFAULT_FAN_OUT_SIZE = 500;

    private PagedQueryParser() {
    }

    public record ParsedPage(int page, int size) {
    }

    public static ParsedPage parse(String query) {
        Map<String, String> params = parseParams(query);
        int page = parsePositiveInt(params.get("page"), DEFAULT_PAGE);
        int size = parsePositiveInt(params.get("size"), DEFAULT_SIZE);
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        return new ParsedPage(page, size);
    }

    /**
     * fan-out 到各节点时使用较大 pageSize，合并后在协调节点再切片。
     */
    public static String forFanOut(String query, int fanOutSize) {
        Map<String, String> params = new LinkedHashMap<>(parseParams(query));
        params.put("page", String.valueOf(DEFAULT_PAGE));
        params.put("size", String.valueOf(Math.max(1, fanOutSize)));
        return toQueryString(params);
    }

    private static Map<String, String> parseParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        String raw = query.startsWith("?") ? query.substring(1) : query;
        if (raw.isBlank()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, idx)), decode(pair.substring(idx + 1)));
            }
        }
        return params;
    }

    private static String toQueryString(Map<String, String> params) {
        if (params.isEmpty()) {
            return "";
        }
        return "?" + params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
