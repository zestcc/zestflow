package com.zestflow.admin.playground;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 试验场执行服务 — 代理到 Executor 触发链执行，记录执行日志
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorProxyService proxyService;
    private final PlaygroundProperties properties;
    private final PlaygroundLogMapper logMapper;

    /**
     * 执行指定场景的链并记录日志
     *
     * @param sceneId 场景标识
     * @param params  用户传入的执行参数
     * @param headers 自定义请求头
     * @param requestIp 请求 IP
     * @return 执行结果
     */
    public Map<String, Object> executeScene(String sceneId, Map<String, Object> params,
                                            Map<String, String> headers, String requestIp) {
        PlaygroundProperties.SceneConfig scene = findScene(sceneId);
        if (scene == null) {
            return Map.of("code", 404, "message", "场景不存在: " + sceneId);
        }

        long startTime = System.currentTimeMillis();
        String appCode = properties.getAppCode();
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
            resultJson = proxyService.executeOnExecutor(appCode, "POST", "/execute", body);
            log.info("Playground 执行完成 scene={} chainCode={} appCode={}", sceneId, scene.getChainCode(), appCode);

            // 解析执行结果
            ObjectNode resultNode = (ObjectNode) MAPPER.readTree(resultJson);
            resultJson = MAPPER.writeValueAsString(resultNode);
            instanceId = resultNode.has("instanceId") ? resultNode.get("instanceId").asText() : "";
            status = resultNode.has("status") && resultNode.get("status").asInt() >= 4 ? 1 : 0;

        } catch (Exception e) {
            log.error("Playground 执行失败 scene={} chainCode={}", sceneId, scene.getChainCode(), e);
            errorMsg = e.getMessage();
            resultJson = "{\"error\":\"" + (errorMsg != null ? errorMsg : "未知错误") + "\"}";
        }

        long costMs = System.currentTimeMillis() - startTime;

        // 保存执行日志
        PlaygroundLogPO logPO = new PlaygroundLogPO();
        logPO.setSceneId(sceneId);
        logPO.setSceneName(scene.getName());
        logPO.setChainCode(scene.getChainCode());
        logPO.setRequestIp(requestIp);
        logPO.setRequestHeaders(headers != null && !headers.isEmpty() ? safeWrite(headers) : null);
        logPO.setParams(safeWrite(params));
        logPO.setResult(resultJson);
        logPO.setInstanceId(instanceId);
        logPO.setStatus(status);
        logPO.setCostMs(costMs);
        logPO.setErrorMsg(errorMsg);
        logPO.setCreatedAt(LocalDateTime.now());
        logMapper.insert(logPO);

        // 组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", status == 1 ? "执行成功" : "执行失败");
        result.put("logId", logPO.getId());
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

    /**
     * 分页查询执行历史（不含 IP）
     */
    public IPage<PlaygroundLogVO> queryHistory(int page, int size) {
        Page<PlaygroundLogPO> poPage = logMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<PlaygroundLogPO>()
                        .orderByDesc(PlaygroundLogPO::getCreatedAt));

        return poPage.convert(this::toVO);
    }

    /**
     * 查询单条执行详情（不含 IP）
     */
    public PlaygroundLogVO getHistoryDetail(Long id) {
        PlaygroundLogPO po = logMapper.selectById(id);
        return po != null ? toVO(po) : null;
    }

    private PlaygroundLogVO toVO(PlaygroundLogPO po) {
        PlaygroundLogVO vo = new PlaygroundLogVO();
        vo.setId(po.getId());
        vo.setSceneId(po.getSceneId());
        vo.setSceneName(po.getSceneName());
        vo.setChainCode(po.getChainCode());
        vo.setInstanceId(po.getInstanceId());
        vo.setStatus(po.getStatus());
        vo.setCostMs(po.getCostMs());
        vo.setErrorMsg(po.getErrorMsg());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setRequestHeaders(safeReadMap(po.getRequestHeaders()));
        vo.setParams(safeReadObject(po.getParams()));
        vo.setResult(safeReadObject(po.getResult()));
        return vo;
    }

    private PlaygroundProperties.SceneConfig findScene(String sceneId) {
        for (PlaygroundProperties.SceneConfig scene : properties.getScenes()) {
            if (scene.getId().equals(sceneId)) {
                return scene;
            }
        }
        return null;
    }

    private String safeWrite(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    private Map<String, String> safeReadMap(String json) {
        if (json == null || json.isBlank()) return null;
        try { return MAPPER.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return null; }
    }

    private Map<String, Object> safeReadObject(String json) {
        if (json == null || json.isBlank()) return null;
        try { return MAPPER.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return null; }
    }
}
