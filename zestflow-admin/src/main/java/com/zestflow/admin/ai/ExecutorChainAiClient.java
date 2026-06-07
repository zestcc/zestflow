package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin → 应用端 Executor 链条 AI 知识库代理（RAG 检索、学习事件、蒸馏均在应用端）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorChainAiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorProxyService executorProxyService;

    public List<String> searchRag(String appCode, String query, int limit) {
        String baseUrl = executorProxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return List.of();
        }
        String path = "/api/ai/rag/search?q=" + urlEncode(query) + "&limit=" + Math.max(1, limit);
        ExecutorProxyService.ExecutorResult result =
                executorProxyService.executeOnExecutorUrl(baseUrl, "GET", path, null);
        return parseStringList(result);
    }

    public Map<String, Object> recordLearningEvent(String appCode, Map<String, Object> body) {
        String baseUrl = executorProxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return Map.of("error", "无可用执行器");
        }
        try {
            String json = MAPPER.writeValueAsString(body);
            ExecutorProxyService.ExecutorResult result = executorProxyService.executeOnExecutorUrl(
                    baseUrl, "POST", "/api/ai/learning/events", json);
            return parseDataMap(result);
        } catch (Exception e) {
            log.warn("记录应用端学习事件失败 appCode={}", appCode, e);
            return Map.of("error", e.getMessage());
        }
    }

    public Map<String, Object> distillPatterns(String appCode, String feature) {
        String baseUrl = executorProxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return Map.of("error", "无可用执行器");
        }
        String path = "/api/ai/patterns/distill"
                + (StringUtils.hasText(feature) ? "?feature=" + urlEncode(feature) : "");
        ExecutorProxyService.ExecutorResult result =
                executorProxyService.executeOnExecutorUrl(baseUrl, "POST", path, "{}");
        return parseDataMap(result);
    }

    private List<String> parseStringList(ExecutorProxyService.ExecutorResult result) {
        if (result == null || !result.isOk() || !StringUtils.hasText(result.getResponseBody())) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(result.getResponseBody());
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (!data.isArray()) {
                return List.of();
            }
            List<String> list = new ArrayList<>();
            for (JsonNode node : data) {
                list.add(node.asText());
            }
            return list;
        } catch (Exception e) {
            log.warn("解析应用端 RAG 响应失败", e);
            return List.of();
        }
    }

    private Map<String, Object> parseDataMap(ExecutorProxyService.ExecutorResult result) {
        if (result == null || !result.isOk() || !StringUtils.hasText(result.getResponseBody())) {
            return Map.of("error", result != null ? result.getMessage() : "请求失败");
        }
        try {
            JsonNode root = MAPPER.readTree(result.getResponseBody());
            JsonNode data = root.has("data") ? root.get("data") : root;
            return MAPPER.convertValue(data, MAPPER.getTypeFactory().constructMapType(
                    LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of("error", "解析响应失败");
        }
    }

    private static String urlEncode(String s) {
        if (s == null) {
            return "";
        }
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
