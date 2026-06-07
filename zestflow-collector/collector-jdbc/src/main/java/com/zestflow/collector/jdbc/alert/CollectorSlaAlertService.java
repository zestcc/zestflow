package com.zestflow.collector.jdbc.alert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.collector.jdbc.registry.CollectorRegistryProperties;
import com.zestflow.common.constant.AdminApiPaths;
import com.zestflow.common.constant.RegistryAuthConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.protocol.EventStats;
import com.zestflow.common.protocol.EventStatsQuery;
import com.zestflow.common.protocol.SlaAlertMetricsReportDTO;
import com.zestflow.common.protocol.SlaAlertScopeDTO;
import com.zestflow.collector.spi.EventQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collector 侧 SLA 扫描 — 读本地 chain_event，配置/邮件由 Admin internal API 处理。
 */
@Slf4j
@RequiredArgsConstructor
public class CollectorSlaAlertService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Result<List<SlaAlertScopeDTO>>> SCOPES_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<Result<Map<String, Object>>> PROCESS_TYPE =
            new TypeReference<>() {};

    private final EventQueryService eventQueryService;
    private final ZestFlowHttpClient httpClient;
    private final CollectorRegistryProperties properties;

    public String scan() {
        List<SlaAlertScopeDTO> scopes = fetchScopes();
        if (scopes.isEmpty()) {
            return "scopes=0 alerts=0 emails=0";
        }
        int alertCount = 0;
        int emailCount = 0;
        long endMs = System.currentTimeMillis();
        for (SlaAlertScopeDTO scope : scopes) {
            if (!scope.isEnabled()) {
                continue;
            }
            long startMs = endMs - scope.getWindowMinutes() * 60_000L;
            EventStats stats = eventQueryService.queryStats(EventStatsQuery.builder()
                    .tenantId(scope.getTenantId())
                    .appCode(scope.getAppCode())
                    .startTime(startMs)
                    .endTime(endMs)
                    .build());
            int[] sent = reportMetrics(scope.getTenantId(), scope.getAppCode(), stats);
            alertCount += sent[0];
            emailCount += sent[1];
        }
        return "scopes=" + scopes.size() + " alerts=" + alertCount + " emails=" + emailCount;
    }

    private List<SlaAlertScopeDTO> fetchScopes() {
        for (String adminUrl : adminUrls()) {
            try {
                String url = adminUrl + AdminApiPaths.of("/internal/alerts/scopes");
                Result<List<SlaAlertScopeDTO>> result = httpClient.get(url, buildHeaders(), SCOPES_TYPE);
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    return result.getData();
                }
            } catch (Exception e) {
                log.warn("拉取 SLA 扫描范围失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return List.of();
    }

    private int[] reportMetrics(Long tenantId, String appCode, EventStats stats) {
        SlaAlertMetricsReportDTO report = SlaAlertMetricsReportDTO.builder()
                .tenantId(tenantId)
                .appCode(appCode)
                .eventStats(stats)
                .build();
        for (String adminUrl : adminUrls()) {
            try {
                String url = adminUrl + AdminApiPaths.of("/internal/alerts/process-metrics");
                Result<Map<String, Object>> result = httpClient.post(url, report, buildHeaders(), PROCESS_TYPE);
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    Map<String, Object> data = result.getData();
                    return new int[]{
                            toInt(data.get("alerts")),
                            toInt(data.get("emails"))
                    };
                }
            } catch (Exception e) {
                log.warn("上报 SLA 指标失败 adminUrl={} tenantId={} appCode={} error={}",
                        adminUrl, tenantId, appCode, e.getMessage());
            }
        }
        return new int[]{0, 0};
    }

    private List<String> adminUrls() {
        if (properties.getAdminAddresses() == null || properties.getAdminAddresses().isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String part : properties.getAdminAddresses().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                urls.add(trimmed);
            }
        }
        return urls;
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        if (properties.getRegistryToken() != null && !properties.getRegistryToken().isBlank()) {
            headers.put(RegistryAuthConstants.REGISTRY_TOKEN_HEADER, properties.getRegistryToken());
        }
        return headers;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
