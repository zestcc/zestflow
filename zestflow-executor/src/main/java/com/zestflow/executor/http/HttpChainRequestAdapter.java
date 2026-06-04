package com.zestflow.executor.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import io.netty.handler.codec.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 将 Servlet 请求适配为 {@link ChainExecuteRequestDTO}。
 */
@Slf4j
public final class HttpChainRequestAdapter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpChainRequestAdapter() {
    }

    public static ChainExecuteRequestDTO fromServlet(HttpServletRequest request, String chainCode) throws IOException {
        Map<String, Object> params = parseBody(request);
        Map<String, String> headers = extractHeaders(request);
        return ChainExecuteRequestDTO.builder()
                .chainCode(chainCode)
                .params(params)
                .headers(headers)
                .idempotencyKey(request.getHeader("X-Idempotency-Key"))
                .traceId(request.getHeader("X-Trace-Id"))
                .build();
    }

    public static Map<String, Object> parseBody(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0) {
            return new HashMap<>();
        }
        String contentType = request.getContentType();
        String text = new String(body, StandardCharsets.UTF_8);
        if (contentType != null && contentType.contains("application/json")) {
            return JSON.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        }
        Map<String, Object> raw = new HashMap<>();
        raw.put("_rawBody", text);
        return raw;
    }

    public static Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Netty /execute 请求体 + 头解析。
     */
    public static ChainExecuteRequestDTO fromNettyBody(String body, HttpHeaders httpHeaders) throws IOException {
        ChainExecuteRequestDTO dto;
        if (body == null || body.isBlank()) {
            dto = ChainExecuteRequestDTO.builder().build();
        } else {
            dto = JSON.readValue(body, ChainExecuteRequestDTO.class);
        }
        return mergeNettyHeaders(dto, httpHeaders);
    }

    static ChainExecuteRequestDTO mergeNettyHeaders(ChainExecuteRequestDTO dto, HttpHeaders httpHeaders) {
        if (dto == null) {
            dto = ChainExecuteRequestDTO.builder().build();
        }
        Map<String, String> merged = new HashMap<>();
        if (dto.getHeaders() != null) {
            merged.putAll(dto.getHeaders());
        }
        if (httpHeaders != null) {
            httpHeaders.forEach(entry -> merged.putIfAbsent(entry.getKey(), entry.getValue()));
        }
        dto.setHeaders(Collections.unmodifiableMap(merged));
        if (dto.getIdempotencyKey() == null || dto.getIdempotencyKey().isBlank()) {
            String key = merged.get("X-Idempotency-Key");
            if (key != null && !key.isBlank()) {
                dto.setIdempotencyKey(key);
            }
        }
        if (dto.getTraceId() == null || dto.getTraceId().isBlank()) {
            String trace = merged.get("X-Trace-Id");
            if (trace != null && !trace.isBlank()) {
                dto.setTraceId(trace);
            }
        }
        return dto;
    }
}
