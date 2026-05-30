package com.zestflow.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.client.ExecutorProxyService.BroadcastResult;
import com.zestflow.admin.client.ExecutorProxyService.ExecutorResult;
import com.zestflow.common.model.event.ChainEventType;
import com.zestflow.common.model.event.PublishEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 链管理 — 所有数据通过 HTTP 代理到具体 Executor 端
 */
@Slf4j
@RestController
@RequestMapping("/chains")
@RequiredArgsConstructor
public class ChainController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 发布进度缓存 chainCode:moduleId → [publishedCount, totalCount] */
    private static final ConcurrentHashMap<String, int[]> PUBLISH_PROGRESS = new ConcurrentHashMap<>();

    private final ExecutorProxyService proxyService;

    @GetMapping
    public String listByModuleId(
            @RequestParam Long moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        String query = "?keyword=" + (keyword != null ? keyword : "")
                + "&status=" + (status != null ? status : "")
                + "&page=" + page + "&size=" + size;
        String json = proxyService.getFromExecutor(moduleId, "/api/chains", query);
        return enrichProgress(json, moduleId);
    }

    /** 注入进度信息：已发布执行器数 / 模块总执行器数 */
    private String enrichProgress(String json, Long moduleId) {
        if (json == null) return json;
        try {
            int totalExecutors = proxyService.resolveAllExecutorUrls(moduleId).size();
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                for (JsonNode record : root.get("records")) {
                    if (record.isObject()) {
                        injectProgress((ObjectNode) record, moduleId, totalExecutors);
                    }
                }
            } else if (root.isObject() && root.has("code")) {
                injectProgress((ObjectNode) root, moduleId, totalExecutors);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("注入进度失败", e);
            return json;
        }
    }

    private void injectProgress(ObjectNode node, Long moduleId, int totalExecutors) {
        String code = node.has("code") ? node.get("code").asText() : "";
        int status = node.has("status") ? node.get("status").asInt() : 0;
        // 优先从发布缓存读取
        int publishedCount = 0;
        int[] cached = PUBLISH_PROGRESS.get(code + ":" + moduleId);
        if (cached != null) {
            publishedCount = cached[0];
            totalExecutors = cached[1];
        } else if (status == 4) {
            // 已发布但无缓存，视为全部完成
            publishedCount = totalExecutors;
        }
        // status=3（发布中）无缓存时保持 publishedCount=0，展示 0/total
        node.put("publishedCount", publishedCount);
        node.put("totalExecutors", totalExecutors);
    }

    @GetMapping("/{code}")
    public String getByCode(@PathVariable String code, @RequestParam Long moduleId) {
        return proxyService.getFromExecutor(moduleId, "/api/chains/" + code, null);
    }

    @PostMapping
    public String create(@RequestBody String bodyJson) {
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(extractModuleId(bodyJson), "POST", "/api/chains", enriched);
    }

    @PutMapping("/{code}")
    public String update(@PathVariable String code, @RequestBody String bodyJson) {
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(extractModuleId(bodyJson), "PUT", "/api/chains/" + code, enriched);
    }

    @DeleteMapping("/{code}")
    public String delete(@PathVariable String code, @RequestParam Long moduleId) {
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(moduleId, "DELETE", "/api/chains/" + code + query, null);
    }

    @PutMapping("/{code}/status")
    public String toggleStatus(@PathVariable String code, @RequestParam Long moduleId) {
        String body = "{\"moduleId\":" + moduleId + "}";
        String enriched = injectUpdatedBy(body);
        return proxyService.executeOnExecutor(moduleId, "PUT", "/api/chains/" + code + "/status", enriched);
    }

    @PostMapping("/{code}/publish")
    public String publish(@PathVariable String code, @RequestParam Long moduleId) {
        java.util.List<String> executorUrls = proxyService.resolveAllExecutorUrls(moduleId);
        if (executorUrls.isEmpty()) {
            return "{\"code\":400,\"message\":\"该模块无可用执行器\",\"total\":0,\"success\":0}";
        }

        String graphData = null;
        try {
            String chainJson = proxyService.getFromExecutor(moduleId, "/api/chains/" + code, null);
            JsonNode chainNode = MAPPER.readTree(chainJson);
            String designCode = chainNode.has("designCode") ? chainNode.get("designCode").asText() : null;
            if (designCode != null && !designCode.isEmpty()) {
                String designJson = proxyService.getFromExecutor(moduleId, "/api/designs/" + designCode, null);
                JsonNode designNode = MAPPER.readTree(designJson);
                if (designNode.has("graphData")) {
                    graphData = designNode.get("graphData").asText();
                }
            }
        } catch (Exception e) {
            log.warn("读取链设计数据失败 code={}", code, e);
        }

        String publishId = UUID.randomUUID().toString();
        PublishEventDTO publishEvent = PublishEventDTO.builder()
                .publishId(publishId)
                .eventType(ChainEventType.PUBLISH_REQUESTED)
                .chainCode(code)
                .moduleId(moduleId)
                .graphData(graphData)
                .totalExecutors(executorUrls.size())
                .timestamp(System.currentTimeMillis())
                .build();

        String eventBody;
        try { eventBody = MAPPER.writeValueAsString(publishEvent); }
        catch (Exception e) { return "{\"code\":500,\"message\":\"序列化失败\"}"; }

        BroadcastResult reloadResult = proxyService.broadcastToExecutors(
                moduleId, "PUT", "/api/chains/" + code + "/reload", eventBody);

        // 缓存发布进度
        PUBLISH_PROGRESS.put(code + ":" + moduleId,
                new int[]{reloadResult.getSuccess(), reloadResult.getTotal()});

        if (reloadResult.isAllSuccess() && reloadResult.getTotal() > 0) {
            String statusPayload = "{\"status\":4,\"moduleId\":" + moduleId + "}";
            proxyService.executeOnExecutor(moduleId, "PUT", "/api/chains/" + code, statusPayload);
            log.info("链发布成功 code={} moduleId={}", code, moduleId);
        }

        log.info("链发布完成 code={} moduleId={} publishId={} success={}/{}",
                code, moduleId, publishId, reloadResult.getSuccess(), reloadResult.getTotal());

        ObjectNode result = MAPPER.createObjectNode();
        result.put("code", reloadResult.isAllSuccess() && reloadResult.getTotal() > 0 ? 200 : 207);
        result.put("publishId", publishId);
        result.put("message", reloadResult.isAllSuccess()
                ? "发布成功"
                : String.format("发布完成: %d/%d 成功", reloadResult.getSuccess(), reloadResult.getTotal()));
        result.put("total", reloadResult.getTotal());
        result.put("success", reloadResult.getSuccess());
        ArrayNode details = result.putArray("details");
        for (ExecutorResult r : reloadResult.getResults()) {
            ObjectNode detail = MAPPER.createObjectNode();
            detail.put("url", r.getUrl());
            detail.put("ok", r.isOk());
            detail.put("message", r.getMessage());
            details.add(detail);
        }
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"code\":500,\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private Long extractModuleId(String bodyJson) {
        try {
            JsonNode json = MAPPER.readTree(bodyJson);
            if (json.has("moduleId") && !json.get("moduleId").isNull()) {
                return json.get("moduleId").asLong();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String injectUpdatedBy(String bodyJson) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(bodyJson);
            String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
            if (username != null) {
                node.put("updatedBy", username);
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return bodyJson;
        }
    }
}
