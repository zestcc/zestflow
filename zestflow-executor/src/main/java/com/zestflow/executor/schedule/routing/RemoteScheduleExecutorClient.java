package com.zestflow.executor.schedule.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Executor → Executor Netty /execute 防腐层（调度远程路由 / Failover）。
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteScheduleExecutorClient {

    private static final TypeReference<ChainExecuteResultDTO> RESULT_TYPE =
            new TypeReference<>() {};

    private final ZestFlowHttpClient httpClient;
    private final ExecutorProperties properties;

    public ChainExecuteResultDTO execute(String host, int port, ChainExecuteRequestDTO request) {
        String url = "http://" + host + ":" + port + "/execute";
        if (request.getSource() == null || request.getSource().isBlank()) {
            request.setSource("executor-schedule-remote");
        }
        try {
            ChainExecuteResultDTO result = httpClient.post(url, request, buildHeaders(), RESULT_TYPE);
            if (result == null) {
                return errorResult(request.getChainCode(), "远程执行器响应为空");
            }
            return result;
        } catch (Exception e) {
            log.warn("远程 Executor 调用失败 host={}:{} chainCode={} error={}",
                    host, port, request.getChainCode(), e.getMessage());
            return errorResult(request.getChainCode(), "远程调用失败: " + e.getMessage());
        }
    }

    private Map<String, String> buildHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("X-Tenant-Id", String.valueOf(properties.getTenantId()));
        if (properties.getAccessToken() != null && !properties.getAccessToken().isEmpty()) {
            headers.put("X-Access-Token", properties.getAccessToken());
        }
        if (properties.getRegistryToken() != null && !properties.getRegistryToken().isEmpty()) {
            headers.put("X-Registry-Token", properties.getRegistryToken());
        }
        return headers;
    }

    private static ChainExecuteResultDTO errorResult(String chainCode, String message) {
        ChainExecuteResultDTO error = new ChainExecuteResultDTO();
        error.setChainCode(chainCode);
        error.setStatus(com.zestflow.common.constant.ChainConstants.CHAIN_FAILED);
        error.setErrorMessage(message);
        return error;
    }
}
