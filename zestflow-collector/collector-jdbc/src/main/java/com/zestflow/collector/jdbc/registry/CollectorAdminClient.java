package com.zestflow.collector.jdbc.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.common.constant.RegistryAuthConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import com.zestflow.common.registry.RegistryRegisterDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集器 Admin 客户端 — 向 Admin 注册、心跳、下线
 */
@Slf4j
@RequiredArgsConstructor
public class CollectorAdminClient {

    private final ZestFlowHttpClient httpClient;
    private final CollectorRegistryProperties properties;

    private static final TypeReference<Result<Void>> RESULT_VOID_TYPE =
            new TypeReference<Result<Void>>() {};

    public boolean register(RegisterDTO dto) {
        List<String> adminList = parseAddresses();
        if (adminList.isEmpty()) {
            log.error("{} collectorId={}",
                    RegistryRegisterDiagnostics.hintForEmptyAddresses("zestflow.collector.registry.admin-addresses"),
                    dto.getExecutorId());
            return false;
        }
        boolean tokenConfigured = hasText(properties.getRegistryToken());
        List<String> failures = new ArrayList<>();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/register";
                Result<Void> result = httpClient.post(url, dto, buildHeaders(), RESULT_VOID_TYPE);
                if (result != null && result.getCode() == 200) {
                    log.info("采集器注册成功 adminUrl={} collectorId={}", adminUrl, dto.getExecutorId());
                    return true;
                }
                String reason = RegistryRegisterDiagnostics.describeResultFailure(result);
                failures.add(adminUrl + " -> " + reason);
                log.warn("采集器注册失败 adminUrl={} reason={}", adminUrl, reason);
            } catch (Throwable e) {
                String reason = RegistryRegisterDiagnostics.describeException(e, tokenConfigured);
                failures.add(adminUrl + " -> " + reason);
                log.warn("采集器注册失败 adminUrl={} reason={}", adminUrl, reason, e);
            }
        }
        log.error(RegistryRegisterDiagnostics.summarizeFailures(
                "采集器", dto.getExecutorId(), properties.getAdminAddresses(),
                "zestflow.collector.registry.admin-addresses", "zestflow.collector.registry-token",
                "/api/registry/collector/register", String.join("; ", failures)));
        return false;
    }

    public boolean heartbeat(HeartbeatDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/heartbeat";
                Result<Void> result = httpClient.post(url, dto, buildHeaders(), RESULT_VOID_TYPE);
                if (result != null && result.getCode() == 200) {
                    return true;
                }
            } catch (Throwable e) {
                log.debug("采集器心跳失败 adminUrl={} error={}", adminUrl, e.getMessage(), e);
            }
        }
        return false;
    }

    public boolean deregister(String collectorId) {
        List<String> adminList = parseAddresses();
        boolean allSuccess = true;
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/" + collectorId;
                Result<Void> result = httpClient.delete(url, buildHeaders(), RESULT_VOID_TYPE);
                if (result == null || result.getCode() != 200) {
                    allSuccess = false;
                }
            } catch (Throwable e) {
                log.warn("采集器注销失败 adminUrl={} error={}", adminUrl, e.getMessage(), e);
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        if (properties.getAccessToken() != null && !properties.getAccessToken().isEmpty()) {
            headers.put("Authorization", "Bearer " + properties.getAccessToken());
        }
        if (properties.getRegistryToken() != null && !properties.getRegistryToken().isEmpty()) {
            headers.put(RegistryAuthConstants.REGISTRY_TOKEN_HEADER, properties.getRegistryToken());
        }
        return headers;
    }

    private List<String> parseAddresses() {
        if (properties.getAdminAddresses() == null) {
            return List.of();
        }
        return Arrays.stream(properties.getAdminAddresses().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
