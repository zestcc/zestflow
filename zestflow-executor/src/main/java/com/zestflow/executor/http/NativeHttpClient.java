package com.zestflow.executor.http;

import com.zestflow.executor.context.ChainContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 无元件绑定的 HTTP 节点原生调用（对标 n8n HTTP Request）。
 */
public final class NativeHttpClient {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private NativeHttpClient() {
    }

    public static String execute(ChainContext ctx, String method) {
        String url = resolveUrl(ctx);
        String httpMethod = method != null && !method.isBlank() ? method.toUpperCase() : "GET";
        String body = ctx.get("_http_body", String.class);
        String contentType = ctx.get("_http_content_type", String.class);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/json";
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));
            switch (httpMethod) {
                case "POST" -> {
                    builder.header("Content-Type", contentType);
                    if (body != null) {
                        builder.POST(HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        builder.POST(HttpRequest.BodyPublishers.noBody());
                    }
                }
                case "PUT" -> {
                    builder.header("Content-Type", contentType);
                    if (body != null) {
                        builder.PUT(HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        builder.PUT(HttpRequest.BodyPublishers.noBody());
                    }
                }
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }
            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ctx.put("_http_status", response.statusCode());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP " + httpMethod + " 请求失败 url=" + url + " error=" + e.getMessage(), e);
        }
    }

    public static String resolveUrl(ChainContext ctx) {
        String url = ctx.get("_http_url", String.class);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("_http_url 不能为空");
        }
        for (Map.Entry<String, Object> entry : ctx.snapshot().entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder) && entry.getValue() != null) {
                url = url.replace(placeholder, entry.getValue().toString());
            }
            String dollar = "${" + entry.getKey() + "}";
            if (url.contains(dollar) && entry.getValue() != null) {
                url = url.replace(dollar, entry.getValue().toString());
            }
        }
        return url;
    }
}
