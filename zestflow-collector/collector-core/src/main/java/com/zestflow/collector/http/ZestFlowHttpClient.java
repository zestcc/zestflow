package com.zestflow.collector.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Admin 通信 HTTP 客户端 — 基于 JDK {@link HttpClient}，不依赖 Spring RestTemplate。
 * <p>
 * 兼容 Spring Boot 3.x / 4.x 下游（Boot 4 中 RestTemplate + HttpHeaders 存在二进制不兼容）。
 */
public class ZestFlowHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration requestTimeout;

    public ZestFlowHttpClient(int timeoutMs) {
        this.requestTimeout = Duration.ofMillis(Math.max(timeoutMs, 1));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.requestTimeout)
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public <T> T get(String url, Map<String, String> headers, TypeReference<T> responseType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .GET();
        applyHeaders(builder, headers);
        return execute(builder.build(), responseType);
    }

    public <T> T post(String url, Object body, Map<String, String> headers, TypeReference<T> responseType) {
        byte[] payload = serialize(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        applyHeaders(builder, headers);
        return execute(builder.build(), responseType);
    }

    public <T> T delete(String url, Map<String, String> headers, TypeReference<T> responseType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .DELETE();
        applyHeaders(builder, headers);
        return execute(builder.build(), responseType);
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.forEach((name, value) -> {
            if (value != null && !value.isEmpty()) {
                builder.header(name, value);
            }
        });
    }

    private byte[] serialize(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private <T> T execute(HttpRequest request, TypeReference<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body() == null || response.body().isBlank()) {
                return null;
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP 请求被中断 url=" + request.uri(), e);
        } catch (Exception e) {
            throw new IllegalStateException("HTTP 请求失败 url=" + request.uri(), e);
        }
    }
}
