package com.zestflow.executor.component.builtin;

import com.zestflow.executor.annotation.*;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 内置 HTTP 调用组件库。
 * <p>
 * 提供开箱即用的 HTTP 远程调用能力，
 * 支持 GET/POST/PUT/DELETE 方法，URL 模板化替换。
 */
@Slf4j
@Component
@ZestComponent("builtin-http")
public class BuiltinHttpComponents {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * HTTP GET 请求
     */
    @ZestHttpClient(value = "httpGet", method = "GET")
    public String httpGet(ChainContext ctx) {
        String url = resolveUrl(ctx);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("httpGet 完成 url={} status={}", url, response.statusCode());
            ctx.put("_http_status", response.statusCode());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET 请求失败 url=" + url + " error=" + e.getMessage(), e);
        }
    }

    /**
     * HTTP POST 请求
     */
    @ZestHttpClient(value = "httpPost", method = "POST")
    public String httpPost(ChainContext ctx) {
        String url = resolveUrl(ctx);
        String body = (String) ctx.get("_http_body");
        String contentType = (String) ctx.get("_http_content_type");
        if (contentType == null) contentType = "application/json";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", contentType);

            if (body != null) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.debug("httpPost 完成 url={} status={}", url, response.statusCode());
            ctx.put("_http_status", response.statusCode());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP POST 请求失败 url=" + url + " error=" + e.getMessage(), e);
        }
    }

    /**
     * HTTP PUT 请求
     */
    @ZestHttpClient(value = "httpPut", method = "PUT")
    public String httpPut(ChainContext ctx) {
        String url = resolveUrl(ctx);
        String body = (String) ctx.get("_http_body");
        String contentType = (String) ctx.get("_http_content_type");
        if (contentType == null) contentType = "application/json";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", contentType);

            if (body != null) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.PUT(HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            log.debug("httpPut 完成 url={} status={}", url, response.statusCode());
            ctx.put("_http_status", response.statusCode());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP PUT 请求失败 url=" + url + " error=" + e.getMessage(), e);
        }
    }

    /**
     * HTTP DELETE 请求
     */
    @ZestHttpClient(value = "httpDelete", method = "DELETE")
    public String httpDelete(ChainContext ctx) {
        String url = resolveUrl(ctx);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("httpDelete 完成 url={} status={}", url, response.statusCode());
            ctx.put("_http_status", response.statusCode());
            return response.body();
        } catch (Exception e) {
            throw new RuntimeException("HTTP DELETE 请求失败 url=" + url + " error=" + e.getMessage(), e);
        }
    }

    private String resolveUrl(ChainContext ctx) {
        String url = (String) ctx.get("_http_url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("_http_url 不能为空");
        }
        // URL 模板替换：将 {key} 替换为上下文中的值
        for (Map.Entry<String, Object> entry : ctx.snapshot().entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (url.contains(placeholder) && entry.getValue() != null) {
                url = url.replace(placeholder, entry.getValue().toString());
            }
        }
        return url;
    }
}