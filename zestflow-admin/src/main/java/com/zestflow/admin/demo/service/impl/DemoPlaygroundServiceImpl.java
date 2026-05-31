package com.zestflow.admin.demo.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.demo.DemoRateLimiter;
import com.zestflow.admin.demo.model.entity.DemoRecordPO;
import com.zestflow.admin.demo.model.entity.DemoScenePO;
import com.zestflow.admin.demo.model.vo.DemoSceneVO;
import com.zestflow.admin.demo.repository.DemoRecordMapper;
import com.zestflow.admin.demo.repository.DemoSceneMapper;
import com.zestflow.admin.demo.service.DemoPlaygroundService;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 试验场执行服务 — 代理到 Executor 触发链执行，记录执行日志
 */
@Slf4j
@ConditionalOnProperty(prefix = "zestflow.demo", name = "enabled", havingValue = "true", matchIfMissing = false)
@Service
@RequiredArgsConstructor
public class DemoPlaygroundServiceImpl implements DemoPlaygroundService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DemoSceneMapper sceneMapper;
    private final DemoRecordMapper recordMapper;
    private final ExecutorProxyService proxyService;
    private final DemoRateLimiter rateLimiter;
    private final TenantAppContext tenantAppContext;

    @Value("${zestflow.demo.app-code:demo-app}")
    private String defaultAppCode;

    @Override
    public Map<String, Object> executeScene(String sceneCode, Map<String, Object> params, String requestIp) {
        DemoScenePO scene = sceneMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DemoScenePO>()
                        .eq(DemoScenePO::getSceneCode, sceneCode));
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
            ChainExecuteRequestDTO request = ChainExecuteRequestDTO.builder()
                    .chainCode(scene.getChainCode())
                    .params(params)
                    .source("playground")
                    .timeoutMs(30_000L)
                    .build();

            String body = MAPPER.writeValueAsString(request);
            resultJson = proxyService.executeOnExecutor(defaultAppCode, "POST", "/execute", body);
            log.info("演示执行完成 sceneCode={} chainCode={}", sceneCode, scene.getChainCode());

            ObjectNode resultNode = (ObjectNode) MAPPER.readTree(resultJson);
            resultJson = MAPPER.writeValueAsString(resultNode);
            instanceId = resultNode.has("instanceId") ? resultNode.get("instanceId").asText() : "";
            status = resultNode.has("status") && resultNode.get("status").asInt() >= 4 ? 1 : 0;

        } catch (Exception e) {
            log.error("演示执行失败 sceneCode={} chainCode={}", sceneCode, scene.getChainCode(), e);
            errorMsg = e.getMessage();
            resultJson = "{\"error\":\"" + (errorMsg != null ? errorMsg : "未知错误") + "\"}";
        }

        long costMs = System.currentTimeMillis() - startTime;

        // 保存执行记录
        DemoRecordPO record = new DemoRecordPO();
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
        record.setAppCode(defaultAppCode);
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
    public DemoSceneVO getSceneInfo(String sceneCode) {
        DemoScenePO po = sceneMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DemoScenePO>()
                        .eq(DemoScenePO::getSceneCode, sceneCode));
        if (po == null) return null;
        DemoSceneVO vo = new DemoSceneVO();
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

    private String safeWrite(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { return null; }
    }
}
