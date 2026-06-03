package com.zestflow.admin.playground.service.impl;



import com.fasterxml.jackson.databind.JsonNode;

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

import com.zestflow.admin.playground.support.PlaygroundAccessControl;

import com.zestflow.admin.playground.support.PlaygroundRecordStorageHelper;

import com.zestflow.admin.playground.support.PlaygroundRequestPathValidator;

import com.zestflow.admin.playground.support.PlaygroundUrlResolver;

import com.zestflow.admin.service.TenantAppContext;

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

                instanceId = resultNode.has("instanceId") ? resultNode.get("instanceId").asText() : "";

                status = resultNode.has("status") && resultNode.get("status").asInt() >= 4 ? 1 : 0;

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

                status = parseBusinessStatus(resultJson);

                if (status == 0) {

                    errorMsg = extractErrorMessage(resultJson);

                }

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

        saveRecord(scene, params, requestIp, resultJson, instanceId, status, costMs, errorMsg);



        Map<String, Object> result = new HashMap<>();

        result.put("code", 200);

        result.put("message", status == 1 ? "执行成功" : "执行失败");

        result.put("costMs", costMs);

        result.put("status", status);

        result.put("errorMsg", errorMsg);

        result.put("sceneName", scene.getName());

        result.put("tip", "执行完成，请前往 Admin 日志页查看完整链路");

        if (instanceId != null && !instanceId.isEmpty()) {

            result.put("instanceId", instanceId);

            result.put("logUrl", "/logs?executionId=" + instanceId);

        }

        try {

            if (resultJson != null) {

                result.put("result", MAPPER.readTree(resultJson));

            }

        } catch (Exception ignored) {

        }

        return result;

    }



    private String executeChain(PlaygroundScenePO scene, Map<String, Object> params) throws Exception {

        ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()

                .chainCode(scene.getChainCode())

                .params(params)

                .source("playground")

                .idempotencyKey("playground-" + scene.getSceneCode() + "-" + UUID.randomUUID())

                .timeoutMs(executeTimeoutMs)

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



    private static int parseBusinessStatus(String resultJson) {

        if (resultJson == null || resultJson.isBlank()) {

            return 0;

        }

        try {

            JsonNode node = MAPPER.readTree(resultJson);

            if (node.has("code")) {

                int code = node.get("code").asInt();

                return code == 200 ? 1 : 0;

            }

            if (node.has("success")) {

                return node.get("success").asBoolean() ? 1 : 0;

            }

            return 1;

        } catch (Exception e) {

            return 0;

        }

    }



    private static String extractErrorMessage(String resultJson) {

        try {

            JsonNode node = MAPPER.readTree(resultJson);

            if (node.has("message")) {

                return node.get("message").asText();

            }

        } catch (Exception ignored) {

        }

        return null;

    }



    private void saveRecord(PlaygroundScenePO scene, Map<String, Object> params, String requestIp,

                            String resultJson, String instanceId, int status, long costMs, String errorMsg) {

        PlaygroundRecordPO record = new PlaygroundRecordPO();

        record.setSceneId(scene.getId());

        record.setSceneName(scene.getName());

        record.setSceneCode(scene.getSceneCode());

        record.setRequestMethod(scene.getRequestMethod());

        record.setRequestPath(scene.getRequestPath());

        record.setBodyType(scene.getBodyType());

        record.setRequestBody(PlaygroundRecordStorageHelper.truncateJson(
                params != null ? safeWrite(params) : null));

        record.setResponseBody(PlaygroundRecordStorageHelper.truncateJson(resultJson));

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


