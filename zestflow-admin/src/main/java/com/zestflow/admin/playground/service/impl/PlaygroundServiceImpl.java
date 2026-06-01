package com.zestflow.admin.playground.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.playground.PlaygroundRateLimiter;
import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;
import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;
import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;
import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;
import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;
import com.zestflow.admin.playground.service.PlaygroundService;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 试验场执行服务 — 代理到 Executor 触发链执行，记录执行日志
 */
@Slf4j
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@Primary
@Service
@RequiredArgsConstructor
public class PlaygroundServiceImpl implements PlaygroundService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PlaygroundSceneMapper sceneMapper;
    private final PlaygroundRecordMapper recordMapper;
    private final ExecutorProxyService proxyService;
    private final PlaygroundRateLimiter rateLimiter;
    private final TenantAppContext tenantAppContext;
    private final RestTemplate restTemplate;

    /** 演示场景链执行超时（毫秒），默认 30s */
    @Value("${zestflow.playground.execute-timeout-ms:30000}")
    private long executeTimeoutMs;

    @Override
    public Map<String, Object> executeScene(String sceneCode, Map<String, Object> params, String requestIp) {
        PlaygroundScenePO scene = sceneMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlaygroundScenePO>()
                        .eq(PlaygroundScenePO::getSceneCode, sceneCode));
        if (scene == null) {
            return Map.of("code", 404, "message", "场景不存在: " + sceneCode);
        }

        // 限流检查
        int rateLimit = scene.getRateLimit() != null ? scene.getRateLimit() : 30;
        if (!rateLimiter.tryAcquire(sceneCode, rateLimit)) {
            return Map.of("code", 429, "message", "请求过于频繁，请稍后再试");
        }

        long startTime = System.currentTimeMillis();
        String resultJson = null;
        String instanceId = null;
        String errorMsg = null;
        int status = 0;

        try {
            String requestPath = scene.getRequestPath();
            // 完整 URL → 直接调用业务端口（如 http://localhost:8080/api/orders/handleApplyAfterSale）
            if (requestPath != null && (requestPath.startsWith("http://") || requestPath.startsWith("https://"))) {
                HttpMethod httpMethod = resolveHttpMethod(scene.getRequestMethod());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, headers);
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            requestPath, httpMethod, entity, String.class);
                    resultJson = response.getBody();
                    status = 1;
                } catch (HttpClientErrorException e) {
                    // 4xx/5xx 是业务错误，标记为失败并记录错误详情
                    resultJson = e.getResponseBodyAsString();
                    if (resultJson == null || resultJson.isBlank()) {
                        resultJson = "{\"error\":\"HTTP " + e.getStatusCode().value() + "\"}";
                    }
                    errorMsg = "业务接口返回 " + e.getStatusCode().value() + ": " + e.getStatusText();
                    status = 0;
                }
                log.info("演示场景直接请求 sceneCode={} method={} url={} status={}", sceneCode, httpMethod, requestPath, status);
            } else {
                ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                        .chainCode(scene.getChainCode())
                        .params(params)
                        .source("playground")
                        .timeoutMs(executeTimeoutMs)
                        .build();

                String body = MAPPER.writeValueAsString(request);
                resultJson = proxyService.executeOnExecutor(scene.getAppCode(), "POST", "/execute", body);
                log.info("演示执行完成 sceneCode={} chainCode={}", sceneCode, scene.getChainCode());

                ObjectNode resultNode = (ObjectNode) MAPPER.readTree(resultJson);
                resultJson = MAPPER.writeValueAsString(resultNode);
                instanceId = resultNode.has("instanceId") ? resultNode.get("instanceId").asText() : "";
                status = resultNode.has("status") && resultNode.get("status").asInt() >= 4 ? 1 : 0;
            }

        } catch (Exception e) {
            log.error("演示执行失败 sceneCode={} chainCode={}", sceneCode, scene.getChainCode(), e);
            errorMsg = e.getMessage();
            resultJson = "{\"error\":\"" + (errorMsg != null ? errorMsg : "未知错误") + "\"}";
        }

        long costMs = System.currentTimeMillis() - startTime;

        // 保存执行记录
        PlaygroundRecordPO record = new PlaygroundRecordPO();
        record.setSceneId(scene.getId());
        record.setSceneName(scene.getName());
        record.setSceneCode(scene.getSceneCode());
        record.setRequestMethod(scene.getRequestMethod());
        record.setRequestPath(scene.getRequestPath());
        record.setBodyType(scene.getBodyType());
        record.setRequestBody(params != null ? safeWrite(params) : null);
        record.setResponseBody(resultJson);
        record.setChainCode(scene.getChainCode());
        record.setInstanceId(instanceId);
        record.setStatus(status);
        record.setCostMs(costMs);
        record.setErrorMsg(errorMsg);
        record.setRequestIp(requestIp);
        record.setTenantId(tenantAppContext.getCurrentTenantId());
        record.setAppCode(scene.getAppCode());
        record.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(record);

        // 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", status == 1 ? "执行成功" : "执行失败");
        result.put("logId", record.getId());
        result.put("instanceId", instanceId);
        result.put("sceneName", scene.getName());
        result.put("costMs", costMs);
        result.put("status", status);
        result.put("errorMsg", errorMsg);
        result.put("tip", "执行完成，请前往 Admin 日志页查看完整链路");
        if (instanceId != null && !instanceId.isEmpty()) {
            result.put("logUrl", "/logs?executionId=" + instanceId);
        }
        try {
            if (resultJson != null) {
                result.put("result", MAPPER.readTree(resultJson));
            }
        } catch (Exception ignored) {}

        return result;
    }

    @Override
    public PlaygroundSceneVO getSceneInfo(String sceneCode) {
        PlaygroundScenePO po = sceneMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlaygroundScenePO>()
                        .eq(PlaygroundScenePO::getSceneCode, sceneCode));
        if (po == null) return null;
        PlaygroundSceneVO vo = new PlaygroundSceneVO();
        vo.setId(po.getId());
        vo.setSceneCode(po.getSceneCode());
        vo.setName(po.getName());
        vo.setDescription(po.getDescription());
        vo.setRequestPath(po.getRequestPath());
        vo.setRequestMethod(po.getRequestMethod());
        vo.setRequestHeaders(po.getRequestHeaders());
        vo.setBodyType(po.getBodyType());
        vo.setRequestBody(po.getRequestBody());
        vo.setResponseExample(po.getResponseExample());
        vo.setChainCode(po.getChainCode());
        vo.setRateLimit(po.getRateLimit());
        return vo;
    }

    /** 场景 HTTP 方法字符串 → HttpMethod，默认 POST */
    private static HttpMethod resolveHttpMethod(String method) {
        if (method == null) return HttpMethod.POST;
        return switch (method.toUpperCase()) {
            case "GET" -> HttpMethod.GET;
            case "PUT" -> HttpMethod.PUT;
            case "DELETE" -> HttpMethod.DELETE;
            default -> HttpMethod.POST;
        };
    }

    private String safeWrite(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { return null; }
    }
}
