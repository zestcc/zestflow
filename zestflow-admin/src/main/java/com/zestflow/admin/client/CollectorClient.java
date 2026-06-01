package com.zestflow.admin.client;

import com.zestflow.admin.client.CollectorConfig.CollectorClientProperties;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.client.dto.EventQueryDTO;
import com.zestflow.admin.client.dto.EventQueryResult;
import com.zestflow.admin.client.dto.ExecutionTraceResult;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Value;

/**
 * Collector HTTP 客户端 — Admin 通过此客户端从 Collector 查询事件数据
 * <p>
 * 防腐层：如果 Collector 切换实现（如从 HTTP 换 gRPC），只需修改此类。
 * 地址解析：优先从注册表查找在线采集器，未配置 api-url 或配置为空时走注册表发现。
 */
@Slf4j
public class CollectorClient {

    private final RestTemplate restTemplate;
    private final String accessToken;
    private final CollectorRegistryService registryService;
    private final CollectorClientProperties properties;

    /** 服务间通信协议（http/https） */
    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    public CollectorClient(RestTemplate restTemplate, String accessToken,
                           CollectorRegistryService registryService,
                           CollectorClientProperties properties) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken;
        this.registryService = registryService;
        this.properties = properties;
    }

    /**
     * 查询事件列表（分页）
     */
    public EventQueryResult queryEvents(EventQueryDTO query) {
        try {
            String url = resolveBaseUrl() + "/collector/events/query";
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
            String url = resolveBaseUrl() + "/collector/events/executions";
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
            String url = resolveBaseUrl() + "/collector/events/executions/" + executionId;
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
            String url = resolveBaseUrl() + "/collector/events/stats";
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
            String url = resolveBaseUrl() + "/collector/health";
            HttpEntity<?> entity = new HttpEntity<>(buildHeaders());
            var result = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
            return result != null && result.get("code") != null && (Integer) result.get("code") == 200;
        } catch (Exception e) {
            log.warn("Collector 健康检查失败");
            return false;
        }
    }

    /**
     * 同步图数据快照到采集器（Admin 发布链时调用）
     * 按 appCode 路由到对应的在线采集器
     */
    public boolean syncSnapshot(ChainSnapshotSyncDTO dto) {
        try {
            String baseUrl = resolveBaseUrl(dto.getAppCode());
            if (baseUrl == null) {
                log.warn("无可用采集器，跳过图数据快照同步 chainCode={}", dto.getChainCode());
                return false;
            }
            String url = baseUrl + "/collector/snapshots";
            HttpEntity<ChainSnapshotSyncDTO> entity = new HttpEntity<>(dto, buildHeaders());
            var response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class).getBody();
            boolean ok = response != null && response.get("code") != null && (Integer) response.get("code") == 200;
            if (ok) {
                log.info("图数据快照同步成功 chainCode={}", dto.getChainCode());
            } else {
                log.warn("图数据快照同步返回异常 chainCode={} response={}", dto.getChainCode(), response);
            }
            return ok;
        } catch (Exception e) {
            log.warn("图数据快照同步失败 chainCode={}", dto.getChainCode(), e);
            return false;
        }
    }

    /**
     * 解析采集器基础地址
     * <p>
     * 优先级：查注册表取第一个在线采集器 > 配置的 api-url（可选兜底）
     */
    private String resolveBaseUrl() {
        return resolveBaseUrl(null);
    }

    /**
     * 按应用编码解析采集器基础地址
     * <p>
     * 优先级：注册表查找 appCode 匹配的在线采集器 > 任意在线采集器 > 配置的 api-url
     */
    private String resolveBaseUrl(String appCode) {
        // 1. 按 appCode 查找
        if (appCode != null) {
            List<CollectorRegistryVO> matched = registryService.listOnlineByAppCode(appCode);
            if (!matched.isEmpty()) {
                return buildUrl(matched.get(0));
            }
        }
        // 2. 任意在线采集器
        List<CollectorRegistryVO> all = registryService.listAllOnline();
        if (!all.isEmpty()) {
            return buildUrl(all.get(0));
        }
        // 3. 配置兜底
        String configuredUrl = properties.getApiUrl();
        if (configuredUrl != null && !configuredUrl.isEmpty()) {
            log.info("注册表中无在线采集器，使用配置的 api-url={}", configuredUrl);
            return configuredUrl;
        }
        log.warn("无在线采集器可用且未配置 zestflow.collector.api-url");
        return null;
    }

    private String buildUrl(CollectorRegistryVO vo) {
        return protocol + "://" + vo.getCollectorHost() + ":" + vo.getCollectorPort();
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
