package com.zestflow.admin.client;

import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;
import com.zestflow.admin.client.dto.ExecutionTraceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Collector HTTP 客户端 — Admin 通过此客户端从 Collector 查询事件数据
 * <p>
 * 防腐层：如果 Collector 切换实现（如从 HTTP 换 gRPC），只需修改此类。
 */
@Slf4j
public class CollectorClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String accessToken;

    public CollectorClient(RestTemplate restTemplate, String apiUrl, String accessToken) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.accessToken = accessToken;
    }

    /**
     * 查询事件列表（分页）
     */
    public EventQueryResult queryEvents(EventQueryDTO query) {
        try {
            String url = apiUrl + "/collector/events/query";
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chainId", query.getChainId());
            requestBody.put("chainName", query.getChainName());
            requestBody.put("executionId", query.getExecutionId());
            requestBody.put("executorId", query.getExecutorId());
            requestBody.put("appName", query.getAppName());
            requestBody.put("eventTypes", query.getEventTypes());
            requestBody.put("startTime", query.getStartTime());
            requestBody.put("endTime", query.getEndTime());
            requestBody.put("status", query.getStatus());
            requestBody.put("keyword", query.getKeyword());
            requestBody.put("page", query.getPage());
            requestBody.put("pageSize", query.getPageSize());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildHeaders());
            EventQueryResult result = restTemplate.exchange(url, HttpMethod.POST, entity, EventQueryResult.class).getBody();
            return result != null ? result : new EventQueryResult();
        } catch (Exception e) {
            log.error("查询事件列表失败", e);
            return new EventQueryResult();
        }
    }

    /**
     * 查询执行轨迹列表
     */
    public EventQueryResult queryExecutionTraces(EventQueryDTO query) {
        try {
            String url = apiUrl + "/collector/events/executions";
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("executionId", query.getExecutionId());
            requestBody.put("chainName", query.getChainName());
            requestBody.put("executorId", query.getExecutorId());
            requestBody.put("appName", query.getAppName());
            requestBody.put("status", query.getStatus());
            requestBody.put("page", query.getPage());
            requestBody.put("pageSize", query.getPageSize());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildHeaders());
            EventQueryResult result = restTemplate.exchange(url, HttpMethod.POST, entity, EventQueryResult.class).getBody();
            return result != null ? result : new EventQueryResult();
        } catch (Exception e) {
            log.error("查询执行轨迹列表失败", e);
            return new EventQueryResult();
        }
    }

    /**
     * 查询单次执行轨迹详情
     */
    @SuppressWarnings("unchecked")
    public ExecutionTraceResult getExecutionTrace(String executionId) {
        try {
            String url = apiUrl + "/collector/events/executions/" + executionId;
            HttpEntity<?> entity = new HttpEntity<>(buildHeaders());
            var response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            if (response != null && response.get("data") instanceof Map) {
                return new ExecutionTraceResult(200, "ok", (Map<String, Object>) response.get("data"));
            }
            return new ExecutionTraceResult(404, "not found", null);
        } catch (Exception e) {
            log.error("查询执行轨迹详情失败 executionId={}", executionId, e);
            return new ExecutionTraceResult(500, e.getMessage(), null);
        }
    }

    /**
     * 查询事件聚合统计
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> queryStats(Long startTime, Long endTime) {
        return queryStats(startTime, endTime, null);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> queryStats(Long startTime, Long endTime, Long tenantId) {
        try {
            String url = apiUrl + "/collector/events/stats";
            Map<String, Object> requestBody = new LinkedHashMap<>();
            if (startTime != null) requestBody.put("startTime", startTime);
            if (endTime != null) requestBody.put("endTime", endTime);
            if (tenantId != null) requestBody.put("tenantId", tenantId);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, buildHeaders());
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
            if (response != null && response.get("data") instanceof Map) {
                return (Map<String, Object>) response.get("data");
            }
            return Map.of();
        } catch (Exception e) {
            log.error("查询事件统计失败", e);
            return Map.of();
        }
    }

    /**
     * 检查 Collector 连通性
     */
    public boolean healthCheck() {
        try {
            String url = apiUrl + "/collector/health";
            HttpEntity<?> entity = new HttpEntity<>(buildHeaders());
            var result = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            return result != null && result.get("code") != null && (Integer) result.get("code") == 200;
        } catch (Exception e) {
            log.warn("Collector 健康检查失败 url={}", apiUrl);
            return false;
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) {
            headers.set("X-Collector-Token", accessToken);
        }
        return headers;
    }
}
