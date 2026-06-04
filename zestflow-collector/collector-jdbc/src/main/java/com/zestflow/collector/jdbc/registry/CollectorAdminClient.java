package com.zestflow.collector.jdbc.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.common.constant.RegistryAuthConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/register";
                Result<Void> result = httpClient.post(url, dto, buildHeaders(), RESULT_VOID_TYPE);
                if (result != null && result.getCode() == 200) {
                    log.info("采集器注册成功 adminUrl={} collectorId={}", adminUrl, dto.getExecutorId());
                    return true;
                }
            } catch (Throwable e) {
                log.warn("采集器注册失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        log.error("所有 Admin 地址注册均失败 collectorId={}", dto.getExecutorId());
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
                log.debug("采集器心跳失败 adminUrl={} error={}", adminUrl, e.getMessage());
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
                log.warn("采集器注销失败 adminUrl={} error={}", adminUrl, e.getMessage());
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
        return List.of(properties.getAdminAddresses().split(","));
    }
}
