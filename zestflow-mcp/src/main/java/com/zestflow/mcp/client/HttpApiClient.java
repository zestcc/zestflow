package com.zestflow.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.mcp.config.McpRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 Admin / Executor HTTP API。
 */
public class HttpApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpRuntimeConfig config;
    private final HttpClient httpClient;

    public HttpApiClient(McpRuntimeConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String listComponents(String appCode) throws Exception {
        if (config.executorUrl() != null && !config.executorUrl().isBlank()) {
            String query = "?page=1&size=500";
            return get(config.executorUrl() + "/api/components" + query, true);
        }
        if (config.adminBaseUrl() == null || config.adminBaseUrl().isBlank()) {
            throw new IllegalStateException("未配置 --executor-url 或 --admin-url，无法列出元件");
        }
        String resolvedApp = appCode != null && !appCode.isBlank() ? appCode : config.appCode();
        String query = "?appCode=" + urlEncode(resolvedApp) + "&page=1&size=500";
        return get(config.adminBaseUrl() + "/components" + query, false);
    }

    public String validateChain(String appCode, String chainDefinitionJson) throws Exception {
        if (config.executorUrl() != null && !config.executorUrl().isBlank()) {
            String body = buildExecutorValidateBody(chainDefinitionJson);
            return post(config.executorUrl() + "/api/chains/validate-definition", body, true);
        }
        if (config.adminBaseUrl() != null && !config.adminBaseUrl().isBlank()) {
            String resolvedApp = appCode != null && !appCode.isBlank() ? appCode : config.appCode();
            String body = MAPPER.writeValueAsString(Map.of(
                    "appCode", resolvedApp,
                    "chainData", chainDefinitionJson == null ? "{}" : chainDefinitionJson));
            return post(config.adminBaseUrl() + "/ai/design/validate", body, false);
        }
        throw new IllegalStateException("未配置 --executor-url 或 --admin-url，无法校验链定义");
    }

    private String buildExecutorValidateBody(String chainDefinitionJson) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        String chainCode = "MCP_VALIDATE";
        Integer version = 1;
        String chainData = chainDefinitionJson == null ? "{}" : chainDefinitionJson;

        if (chainDefinitionJson != null && !chainDefinitionJson.isBlank()) {
            JsonNode chain = MAPPER.readTree(chainDefinitionJson);
            if (chain.hasNonNull("code")) {
                chainCode = chain.get("code").asText(chainCode);
            }
            if (chain.hasNonNull("version")) {
                version = chain.get("version").asInt(1);
            }
        }

        root.put("chainCode", chainCode);
        root.put("version", version);
        root.put("chainData", chainData);
        return MAPPER.writeValueAsString(root);
    }

    public Map<String, Object> parseValidationResponse(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode data = root.has("data") ? root.get("data") : root;
        boolean valid = data.path("valid").asBoolean(false);
        List<String> errors = new ArrayList<>();
        if (data.has("errors") && data.get("errors").isArray()) {
            for (JsonNode err : data.get("errors")) {
                errors.add(err.asText());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("errors", errors);
        return result;
    }

    private String get(String url, boolean executorMode) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET();
        applyAuth(builder, executorMode);
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            log.warn("GET {} -> {}", url, response.statusCode());
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String post(String url, String body, boolean executorMode) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyAuth(builder, executorMode);
        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            log.warn("POST {} -> {}", url, response.statusCode());
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private void applyAuth(HttpRequest.Builder builder, boolean executorMode) {
        if (executorMode) {
            if (config.executorAccessToken() != null && !config.executorAccessToken().isBlank()) {
                builder.header("X-Access-Token", config.executorAccessToken());
            }
            return;
        }
        if (config.bearerToken() != null && !config.bearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + config.bearerToken());
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
