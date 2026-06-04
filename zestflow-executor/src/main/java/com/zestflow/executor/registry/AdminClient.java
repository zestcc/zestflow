package com.zestflow.executor.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.common.constant.RegistryAuthConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainSyncDTO;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class AdminClient {

    private final ZestFlowHttpClient httpClient;
    private final ExecutorProperties properties;

    private static final TypeReference<Result<Void>> RESULT_VOID_TYPE =
            new TypeReference<Result<Void>>() {};

    private static final TypeReference<Result<List<String>>> RESULT_LIST_STRING_TYPE =
            new TypeReference<Result<List<String>>>() {};

    private static final TypeReference<Result<ChainDefinitionDTO>> RESULT_CHAIN_DEF_TYPE =
            new TypeReference<Result<ChainDefinitionDTO>>() {};

    public boolean register(RegisterDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/register";
                Result<Void> result = httpClient.post(url, dto, buildHeaders(), RESULT_VOID_TYPE);
                if (result != null && result.getCode() == 200) {
                    log.info("注册成功 adminUrl={} executorId={}", adminUrl, dto.getExecutorId());
                    return true;
                }
            } catch (Throwable e) {
                log.warn("注册失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        log.error("所有 Admin 地址注册均失败 executorId={}", dto.getExecutorId());
        return false;
    }

    public boolean heartbeat(HeartbeatDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/heartbeat";
                Result<Void> result = httpClient.post(url, dto, buildHeaders(), RESULT_VOID_TYPE);
                if (result != null && result.getCode() == 200) {
                    return true;
                }
            } catch (Throwable e) {
                log.debug("心跳失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return false;
    }

    public boolean deregister(String executorId) {
        List<String> adminList = parseAddresses();
        boolean allSuccess = true;
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/" + executorId;
                Result<Void> result = httpClient.delete(url, buildHeaders(), RESULT_VOID_TYPE);
                if (result == null || result.getCode() != 200) {
                    allSuccess = false;
                }
            } catch (Throwable e) {
                log.warn("注销失败 adminUrl={} error={}", adminUrl, e.getMessage());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    public List<String> fetchActiveChainCodes(String appCode) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/active-codes?appCode=" + appCode;
                Result<List<String>> result = httpClient.get(url, buildHeaders(), RESULT_LIST_STRING_TYPE);
                if (result != null && result.getCode() == 200 && result.getData() != null) {
                    return result.getData();
                }
            } catch (Throwable e) {
                log.warn("获取活跃链列表失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    public ChainDefinitionDTO fetchChainDefinition(String code) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/code/" + code;
                Result<ChainDefinitionDTO> result = httpClient.get(url, buildHeaders(), RESULT_CHAIN_DEF_TYPE);
                if (result != null && result.getCode() == 200) {
                    return result.getData();
                }
            } catch (Throwable e) {
                log.warn("获取链定义失败 code={} adminUrl={} error={}", code, adminUrl, e.getMessage());
            }
        }
        return null;
    }

    public void notifyChainSync(ChainSyncDTO sync) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/sync";
                httpClient.post(url, sync, buildHeaders(), RESULT_VOID_TYPE);
                return;
            } catch (Throwable e) {
                log.warn("通知链同步失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("X-Tenant-Id", String.valueOf(properties.getTenantId()));
        if (properties.getAccessToken() != null && !properties.getAccessToken().isEmpty()) {
            headers.put("Authorization", "Bearer " + properties.getAccessToken());
        }
        if (properties.getRegistryToken() != null && !properties.getRegistryToken().isEmpty()) {
            headers.put(RegistryAuthConstants.REGISTRY_TOKEN_HEADER, properties.getRegistryToken());
        }
        return headers;
    }

    private List<String> parseAddresses() {
        return Arrays.stream(properties.getAdminAddresses().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
