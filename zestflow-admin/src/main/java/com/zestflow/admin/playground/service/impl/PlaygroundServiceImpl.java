package com.zestflow.admin.playground.service.impl;



import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.zestflow.admin.config.PlaygroundPlatformConfig;
import com.zestflow.admin.playground.PlaygroundRateLimiter;

import com.zestflow.admin.playground.model.entity.PlaygroundRecordPO;

import com.zestflow.admin.playground.model.entity.PlaygroundScenePO;

import com.zestflow.admin.playground.model.vo.PlaygroundSceneVO;

import com.zestflow.admin.playground.repository.PlaygroundRecordMapper;

import com.zestflow.admin.playground.repository.PlaygroundSceneMapper;

import com.zestflow.admin.playground.service.PlaygroundService;

import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.common.protocol.InvocationPayloadDTO;

import com.zestflow.admin.playground.support.PlaygroundAccessControl;

import com.zestflow.admin.playground.support.PlaygroundRecordStorageHelper;

import com.zestflow.admin.playground.support.PlaygroundRequestPathValidator;

import com.zestflow.admin.playground.support.PlaygroundUrlResolver;

import com.zestflow.admin.service.TenantAppContext;

import com.zestflow.common.constant.ChainConstants;

import com.zestflow.common.model.dto.ChainExecuteRequestDTO;

import com.zestflow.common.util.PlaygroundUrlHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.context.annotation.Primary;

import org.springframework.stereotype.Service;



import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

import java.time.LocalDateTime;

import java.util.HashMap;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;



/**

 * 试验场执行服务 — 代理到 Executor Netty 触发链执行或业务 API，记录执行日志

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

    private final PlaygroundAccessControl accessControl;

    private final PlaygroundUrlResolver playgroundUrlResolver;

    private final CollectorQueryAggregator collectorQueryAggregator;

    private final PlaygroundPlatformConfig playgroundPlatformConfig;



    @Override

    public Map<String, Object> executeScene(String sceneCode, Map<String, Object> params, String requestIp) {

        PlaygroundScenePO scene = sceneMapper.selectOne(

                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlaygroundScenePO>()

                        .eq(PlaygroundScenePO::getSceneCode, sceneCode));

        if (scene == null) {

            return Map.of("code", 404, "message", "场景不存在: " + sceneCode);

        }



        accessControl.requireAppPermission(scene.getAppCode(), "APP_EDITOR");

        String appCode = scene.getAppCode();
        String requestPath = playgroundUrlResolver.stripInternalAbsoluteUrl(scene.getRequestPath());
        PlaygroundRequestPathValidator.validate(requestPath, playgroundUrlResolver.allowedBaseUrls(appCode));



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

            if (playgroundUrlResolver.isExecutePath(requestPath)) {

                resultJson = executeChain(scene, params);

                ObjectNode resultNode = (ObjectNode) MAPPER.readTree(resultJson);

                resultJson = MAPPER.writeValueAsString(resultNode);

                instanceId = extractExecutionId(resultJson);

                ExecutionStatus resolved = resolveExecutionStatus(resultJson);

                status = resolved.status();

                errorMsg = resolved.errorMsg();

            } else if (playgroundUrlResolver.isApiPath(requestPath)) {

                String method = scene.getRequestMethod() != null ? scene.getRequestMethod() : "POST";

                String reqBody = "GET".equalsIgnoreCase(method) ? null : MAPPER.writeValueAsString(params);

                if (playgroundUrlResolver.isTomcatBusinessUrl(appCode, requestPath)) {
                    String targetUrl = appendGetQueryToFullUrl(requestPath, method, params);
                    resultJson = proxyService.executeOnExternalUrl(targetUrl, method, reqBody);
                } else {
                    String path = buildApiRelativePath(requestPath, method, params);
                    resultJson = proxyService.executeOnExecutor(appCode, method, path, reqBody);
                }

                ExecutionStatus resolved = resolveExecutionStatus(resultJson);

                status = resolved.status();

                errorMsg = resolved.errorMsg();

                instanceId = extractExecutionId(resultJson);

                log.info("演示场景业务 API sceneCode={} path={} status={}", sceneCode, requestPath, status);

            } else {

                return Map.of("code", 400, "message", "不支持的请求路径: " + requestPath);

            }

        } catch (Exception e) {

            log.error("演示执行失败 sceneCode={} chainCode={}", sceneCode, scene.getChainCode(), e);

            errorMsg = e.getMessage();

            resultJson = "{\"error\":\"" + (errorMsg != null ? errorMsg.replace("\"", "'") : "未知错误") + "\"}";

        }



        long costMs = System.currentTimeMillis() - startTime;

        if (instanceId == null || instanceId.isBlank()) {
            instanceId = extractExecutionId(resultJson);
        }

        saveRecord(scene, params, requestIp, resultJson, instanceId, status, costMs, errorMsg);



        Map<String, Object> result = new HashMap<>();

        result.put("code", 200);

        result.put("message", status == 1 ? "执行成功" : "执行失败");

        result.put("costMs", costMs);

        result.put("status", status);

        result.put("errorMsg", errorMsg);

        result.put("sceneName", scene.getName());

        if (instanceId != null && !instanceId.isEmpty()) {
            result.put("tip", "执行完成，可点击「查看日志」查看完整链路");
        } else {
            result.put("tip", status == 1 ? "执行成功" : "执行失败");
        }

        if (instanceId != null && !instanceId.isEmpty()) {

            result.put("instanceId", instanceId);

            result.put("logUrl", "/logs?executionId=" + instanceId);

        }

        putResponsePayload(result, resultJson);

        return result;

    }



    private String executeChain(PlaygroundScenePO scene, Map<String, Object> params) throws Exception {

        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()

                .chainCode(scene.getChainCode())

                .params(params)

                .source("playground")

                .idempotencyKey("playground-" + scene.getSceneCode() + "-" + UUID.randomUUID())

                .timeoutMs((long) playgroundPlatformConfig.getExecuteTimeoutMs())

                .build();

        String body = MAPPER.writeValueAsString(request);

        String resultJson = proxyService.executeOnExecutor(scene.getAppCode(), "POST", "/execute", body);

        log.info("演示链执行完成 sceneCode={} chainCode={}", scene.getSceneCode(), scene.getChainCode());

        return resultJson;

    }



    /** GET 请求将 params 拼到相对路径 */
    private static String buildApiRelativePath(String requestPath, String method, Map<String, Object> params) {
        String relative = requestPath.contains("://")
                ? PlaygroundUrlHelper.toRelativePath(requestPath)
                : requestPath;
        if (!"GET".equalsIgnoreCase(method) || params == null || params.isEmpty()) {
            return relative;
        }
        String basePath = relative.split("\\?")[0];
        String query = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return basePath + "?" + query;
    }

    /** GET 请求将 params 拼到完整 URL（Tomcat 通道） */
    private static String appendGetQueryToFullUrl(String fullUrl, String method, Map<String, Object> params) {

        if (!"GET".equalsIgnoreCase(method) || params == null || params.isEmpty()) {

            return fullUrl;

        }

        String basePath = fullUrl.split("\\?")[0];

        String query = params.entrySet().stream()

                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))

                .collect(Collectors.joining("&"));

        return basePath + "?" + query;

    }



    /** 从 Executor 响应中提取链实例 ID（兼容 instanceId / executionId / orderId） */
    private static String extractExecutionId(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return extractExecutionId(MAPPER.readTree(resultJson));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractExecutionId(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String id = firstNonBlankText(node, "instanceId", "executionId", "orderId");
        if (id != null) {
            return id;
        }
        if (node.has("data") && node.get("data").isObject()) {
            return firstNonBlankText(node.get("data"), "instanceId", "executionId", "orderId");
        }
        return null;
    }

    private static String firstNonBlankText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.get(field).isValueNode()) {
                String text = node.get(field).asText("");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * 试验场成功判定 — 优先链/节点执行结果，不以业务 Result.code 作为唯一依据。
     * <p>
     * 1. 链级 status 为 {@link ChainConstants#CHAIN_SUCCESS} 时视为成功（含 CONTINUE 策略下节点失败但链成功）<br>
     * 2. 含 nodeResults → 无失败节点即为成功<br>
     * 3. 非 JSON（XML/纯文本）或非标准 JSON → 有响应体即成功（代理 HTTP 已成功）
     */
    static ExecutionStatus resolveExecutionStatus(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return ExecutionStatus.failure("响应为空");
        }
        try {
            JsonNode node = MAPPER.readTree(resultJson);
            ExecutionStatus fromChain = resolveChainExecutionStatus(node);
            if (fromChain != null && fromChain.status() == 1) {
                return fromChain;
            }
            if (node.has("data") && node.get("data").isObject()) {
                ExecutionStatus nestedChain = resolveChainExecutionStatus(node.get("data"));
                if (nestedChain != null && nestedChain.status() == 1) {
                    return nestedChain;
                }
                if (fromChain == null) {
                    fromChain = nestedChain;
                }
            }
            ExecutionStatus fromNodes = resolveNodeResultsStatus(node);
            if (fromNodes != null) {
                return fromNodes;
            }
            if (node.has("data") && node.get("data").isObject()) {
                fromNodes = resolveNodeResultsStatus(node.get("data"));
                if (fromNodes != null) {
                    return fromNodes;
                }
            }
            if (fromChain != null) {
                return fromChain;
            }
            return ExecutionStatus.success();
        } catch (Exception e) {
            return ExecutionStatus.success();
        }
    }

    private static ExecutionStatus resolveChainExecutionStatus(JsonNode node) {
        if (node == null || !node.has("status") || !node.get("status").isNumber()) {
            return null;
        }
        int chainStatus = node.get("status").asInt();
        if (chainStatus < ChainConstants.CHAIN_INIT || chainStatus > ChainConstants.CHAIN_STOPPED) {
            return null;
        }
        if (chainStatus == ChainConstants.CHAIN_SUCCESS) {
            return ExecutionStatus.success();
        }
        String error = firstNonBlankText(node, "errorMessage", "message");
        if (error == null) {
            error = "链执行未成功，status=" + chainStatus;
        }
        return ExecutionStatus.failure(error);
    }

    private static ExecutionStatus resolveNodeResultsStatus(JsonNode node) {
        if (node == null || !node.has("nodeResults") || !node.get("nodeResults").isArray()) {
            return null;
        }
        JsonNode nodeResults = node.get("nodeResults");
        if (nodeResults.isEmpty()) {
            return ExecutionStatus.success();
        }
        for (JsonNode nr : nodeResults) {
            if (nr.has("status") && nr.get("status").asInt() == ChainConstants.NODE_FAILED) {
                String err = nr.has("errorMessage") && !nr.get("errorMessage").isNull()
                        ? nr.get("errorMessage").asText()
                        : null;
                if (err == null && nr.has("nodeId")) {
                    err = "节点执行失败: " + nr.get("nodeId").asText();
                }
                return ExecutionStatus.failure(err != null ? err : "节点执行失败");
            }
        }
        return ExecutionStatus.success();
    }

    private static void putResponsePayload(Map<String, Object> result, String resultJson) {
        if (resultJson == null) {
            return;
        }
        try {
            result.put("result", MAPPER.readTree(resultJson));
        } catch (Exception e) {
            result.put("result", resultJson);
        }
    }

    record ExecutionStatus(int status, String errorMsg) {
        static ExecutionStatus success() {
            return new ExecutionStatus(1, null);
        }

        static ExecutionStatus failure(String errorMsg) {
            return new ExecutionStatus(0, errorMsg);
        }
    }



    private void saveRecord(PlaygroundScenePO scene, Map<String, Object> params, String requestIp,

                            String resultJson, String instanceId, int status, long costMs, String errorMsg) {

        String invocationId = UUID.randomUUID().toString().replace("-", "");
        String requestBody = PlaygroundRecordStorageHelper.truncateJson(
                params != null ? safeWrite(params) : null);
        String responseBody = PlaygroundRecordStorageHelper.truncateJson(resultJson);

        InvocationPayloadDTO payload = InvocationPayloadDTO.builder()
                .invocationId(invocationId)
                .sourceType("PLAYGROUND")
                .executionId(instanceId)
                .sceneCode(scene.getSceneCode())
                .requestBody(requestBody)
                .responseBody(responseBody)
                .tenantId(tenantAppContext.getCurrentTenantId())
                .appCode(scene.getAppCode())
                .build();
        if (!collectorQueryAggregator.saveInvocationPayload(payload)) {
            log.warn("试验场载荷写入 app_log 失败 invocationId={} sceneCode={}", invocationId, scene.getSceneCode());
        }

        PlaygroundRecordPO record = new PlaygroundRecordPO();

        record.setSceneId(scene.getId());

        record.setSceneName(scene.getName());

        record.setSceneCode(scene.getSceneCode());

        record.setRequestMethod(scene.getRequestMethod());

        record.setRequestPath(scene.getRequestPath());

        record.setBodyType(scene.getBodyType());

        record.setInvocationId(invocationId);

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

    }



    @Override

    public PlaygroundSceneVO getSceneInfo(String sceneCode) {

        PlaygroundScenePO po = sceneMapper.selectOne(

                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlaygroundScenePO>()

                        .eq(PlaygroundScenePO::getSceneCode, sceneCode));

        if (po == null) {

            return null;

        }

        accessControl.requireAppPermission(po.getAppCode(), "APP_VIEWER");

        return toSceneVo(po);

    }



    private PlaygroundSceneVO toSceneVo(PlaygroundScenePO po) {

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

        vo.setAppCode(po.getAppCode());

        return vo;

    }



    private String safeWrite(Object obj) {

        try {

            return MAPPER.writeValueAsString(obj);

        } catch (Exception e) {

            return null;

        }

    }

}


