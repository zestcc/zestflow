package com.zestflow.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.client.CollectorClient;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.client.ExecutorProxyService.BroadcastResult;
import com.zestflow.admin.client.ExecutorProxyService.ExecutorResult;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.runtime.AdminRuntimeStateStore;
import com.zestflow.admin.service.ExecutorRegistryService;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import com.zestflow.common.model.event.ChainEventType;
import com.zestflow.common.model.event.PublishEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * 链管理 — 所有数据通过 HTTP 代理到具体 Executor 端
 */
@Slf4j
@RestController
@RequestMapping("/chains")
@RequiredArgsConstructor
public class ChainController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorProxyService proxyService;
    private final PermissionService permissionService;
    private final CollectorClient collectorClient;
    private final AdminRuntimeStateStore runtimeStateStore;
    private final ExecutorRegistryService executorRegistryService;

    @GetMapping
    public String listByAppCode(
            @RequestParam String appCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        requireAppPermission(appCode, "APP_VIEWER");
        String query = "?keyword=" + (keyword != null ? keyword : "")
                + "&status=" + (status != null ? status : "")
                + "&page=" + page + "&size=" + size;
        String json = proxyService.getFromExecutor(appCode, "/api/chains", query);
        return enrichProgress(json, appCode);
    }

    /** 注入进度信息：已发布执行器数 / 应用总执行器数 */
    private String enrichProgress(String json, String appCode) {
        if (json == null) return json;
        try {
            int totalExecutors = proxyService.resolveAllExecutorUrls(appCode).size();
            Set<String> declaredKeys = executorRegistryService.listDeclaredChainKeysByApp(appCode);
            JsonNode root = MAPPER.readTree(json);
            if (root.has("records")) {
                for (JsonNode record : root.get("records")) {
                    if (record.isObject()) {
                        injectProgress((ObjectNode) record, appCode, totalExecutors, declaredKeys);
                    }
                }
            } else if (root.isObject() && root.has("code")) {
                injectProgress((ObjectNode) root, appCode, totalExecutors, declaredKeys);
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("注入进度失败", e);
            return json;
        }
    }

    private void injectProgress(ObjectNode node, String appCode, int totalExecutors, Set<String> declaredKeys) {
        String code = node.has("code") ? node.get("code").asText() : "";
        int status = node.has("status") ? node.get("status").asInt() : 0;
        // 优先从发布缓存读取
        int publishedCount = 0;
        int[] cached = runtimeStateStore.getPublishProgress(code).orElse(null);
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
        String chainKey = node.has("chainKey") ? node.get("chainKey").asText("") : "";
        boolean appDeclared = chainKey != null && !chainKey.isBlank() && declaredKeys.contains(chainKey.trim());
        node.put("appDeclared", appDeclared);
    }

    @GetMapping("/active-codes")
    public String fetchActiveCodes(@RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        return proxyService.getArrayFromExecutor(appCode, "/api/chains/active-codes", null);
    }

    /**
     * 获取链的完整定义（含设计数据），供 Executor 拉取热加载
     */
    @GetMapping("/code/{code}")
    public String fetchChainDefinition(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        ObjectNode defNode = MAPPER.createObjectNode();
        try {
            String chainJson = proxyService.getFromExecutor(appCode, "/api/chains/" + code, null);
            if (chainJson == null || chainJson.contains("\"code\":404")) {
                return "{\"code\":404,\"message\":\"链不存在\"}";
            }
            JsonNode chainNode = MAPPER.readTree(chainJson);
            defNode.put("code", code);
            String designCode = chainNode.has("designCode") ? chainNode.get("designCode").asText() : "";
            defNode.put("designCode", designCode);
            if (!designCode.isEmpty()) {
                String designJson = proxyService.getFromExecutor(appCode, "/api/designs/" + designCode, null);
                if (designJson != null) {
                    JsonNode designNode = MAPPER.readTree(designJson);
                    if (designNode.has("graphData")) {
                        defNode.put("graphData", designNode.get("graphData").asText());
                    }
                    if (designNode.has("chainData") && !designNode.get("chainData").asText().isEmpty()) {
                        defNode.put("chainData", designNode.get("chainData").asText());
                    }
                }
            }
            return MAPPER.writeValueAsString(defNode);
        } catch (Exception e) {
            log.warn("获取链定义失败 code={}", code, e);
            return "{\"code\":500,\"message\":\"获取链定义失败\"}";
        }
    }

    /** 链同步状态 TTL（内存/Redis 统一过期策略） */
    /**
     * 接收 Executor 上报的链同步状态
     */
    @PostMapping("/sync")
    public String receiveChainSync(@RequestBody String syncJson) {
        try {
            com.zestflow.common.model.dto.ChainSyncDTO sync = MAPPER.readValue(syncJson, com.zestflow.common.model.dto.ChainSyncDTO.class);
            if (sync.getExecutorId() == null || sync.getExecutorId().isBlank()) {
                return "{\"code\":400,\"message\":\"executorId 不能为空\"}";
            }
            if (sync.getTimestamp() == null) {
                sync.setTimestamp(System.currentTimeMillis());
            }
            runtimeStateStore.saveChainSync(sync);
            log.info("收到链同步通知 executorId={} status={} loaded={}", sync.getExecutorId(), sync.getStatus(),
                    sync.getLoadedChains() != null ? sync.getLoadedChains().size() : 0);
        } catch (Exception e) {
            log.warn("解析链同步通知失败", e);
            return "{\"code\":400,\"message\":\"解析失败\"}";
        }
        return "{\"code\":200,\"message\":\"接收成功\"}";
    }

    @GetMapping("/sync/status")
    public String getSyncStatus(@RequestParam(required = false) String executorId) {
        try {
            if (executorId != null && !executorId.isEmpty()) {
                com.zestflow.common.model.dto.ChainSyncDTO sync = runtimeStateStore.getChainSync(executorId).orElse(null);
                if (sync == null) {
                    return "{\"code\":404,\"message\":\"未找到同步记录\"}";
                }
                return MAPPER.writeValueAsString(sync);
            }
            return MAPPER.writeValueAsString(runtimeStateStore.getAllChainSync());
        } catch (Exception e) {
            return "{\"code\":500,\"message\":\"查询同步状态失败\"}";
        }
    }

    @GetMapping("/{code}")
    public String getByCode(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        String json = proxyService.getFromExecutor(appCode, "/api/chains/" + code, null);
        return enrichProgress(json, appCode);
    }

    @PostMapping
    public String create(@RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "POST", "/api/chains", enriched);
    }

    @PutMapping("/{code}")
    public String update(@PathVariable String code, @RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "PUT", "/api/chains/" + code, enriched);
    }

    @DeleteMapping("/{code}")
    public String delete(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_ADMIN");
        try {
            String chainJson = proxyService.getFromExecutor(appCode, "/api/chains/" + code, null);
            JsonNode chainNode = MAPPER.readTree(chainJson);
            String chainKey = chainNode.has("chainKey") ? chainNode.get("chainKey").asText("") : "";
            if (!chainKey.isBlank() && executorRegistryService.isChainKeyDeclared(appCode, chainKey)) {
                return "{\"code\":409,\"message\":\"应用仍声明该链 chain_key="
                        + escapeJson(chainKey) + "，请先从代码移除 @ZestChain 后重启\"}";
            }
        } catch (Exception e) {
            log.warn("删除前 chain_key 校验失败 code={} appCode={}", code, appCode, e);
        }
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(appCode, "DELETE", "/api/chains/" + code + query, null);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @PutMapping("/{code}/status")
    public String toggleStatus(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_EDITOR");
        String body = "{\"appCode\":\"" + appCode + "\"}";
        String enriched = injectUpdatedBy(body);
        return proxyService.executeOnExecutor(appCode, "PUT", "/api/chains/" + code + "/status", enriched);
    }

    @PostMapping("/{code}/publish")
    public String publish(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_ADMIN");
        java.util.List<String> executorUrls = proxyService.resolveAllExecutorUrls(appCode);
        if (executorUrls.isEmpty()) {
            return "{\"code\":400,\"message\":\"该应用无可用执行器\",\"total\":0,\"success\":0}";
        }

        String graphData = null;
        String chainData = null;
        try {
            String chainJson = proxyService.getFromExecutor(appCode, "/api/chains/" + code, null);
            JsonNode chainNode = MAPPER.readTree(chainJson);
            String designCode = chainNode.has("designCode") ? chainNode.get("designCode").asText() : null;
            if (designCode != null && !designCode.isEmpty()) {
                String designJson = proxyService.getFromExecutor(appCode, "/api/designs/" + designCode, null);
                JsonNode designNode = MAPPER.readTree(designJson);
                int designStatus = designNode.has("status") && !designNode.get("status").isNull()
                        ? designNode.get("status").asInt() : 0;
                if (designStatus != 1) {
                    log.warn("发布失败：关联设计未启用 chainCode={} designCode={} status={}",
                            code, designCode, designStatus);
                    return "{\"code\":400,\"message\":\"关联设计未启用，无法发布\",\"total\":0,\"success\":0}";
                }
                if (designNode.has("graphData")) {
                    graphData = designNode.get("graphData").asText();
                }
                if (designNode.has("chainData") && !designNode.get("chainData").asText().isEmpty()) {
                    chainData = designNode.get("chainData").asText();
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
                .graphData(graphData)
                .chainData(chainData)
                .totalExecutors(executorUrls.size())
                .timestamp(System.currentTimeMillis())
                .build();

        String eventBody;
        try { eventBody = MAPPER.writeValueAsString(publishEvent); }
        catch (Exception e) { return "{\"code\":500,\"message\":\"序列化失败\"}"; }

        // 读取当前链数据作为回滚基线
        // 读取当前链数据作为回滚基线（部分失败时回滚成功执行器）
        String rollbackGraphData = null;
        String rollbackChainData = null;
        try {
            String chainJson = proxyService.getFromExecutor(appCode, "/api/chains/" + code, null);
            if (chainJson != null) {
                JsonNode chainNode = MAPPER.readTree(chainJson);
                String designCode = chainNode.has("designCode") ? chainNode.get("designCode").asText() : null;
                if (designCode != null && !designCode.isEmpty()) {
                    String designJson = proxyService.getFromExecutor(appCode, "/api/designs/" + designCode, null);
                    if (designJson != null) {
                        JsonNode designNode = MAPPER.readTree(designJson);
                        if (designNode.has("graphData"))
                            rollbackGraphData = designNode.get("graphData").asText();
                        if (designNode.has("chainData"))
                            rollbackChainData = designNode.get("chainData").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取回滚基线数据失败 code={}", code, e);
        }

        BroadcastResult reloadResult = proxyService.broadcastToExecutors(
                appCode, "PUT", "/api/chains/" + code + "/reload", eventBody);

        // 部分失败时回滚已成功的执行器
        if (!reloadResult.isAllSuccess() && reloadResult.getSuccess() > 0 && rollbackGraphData != null) {
            log.warn("发布部分失败，开始回滚已成功的执行器 code={} success={}/{}",
                    code, reloadResult.getSuccess(), reloadResult.getTotal());
            PublishEventDTO rollbackEvent = PublishEventDTO.builder()
                    .publishId("rollback-" + publishId)
                    .eventType(ChainEventType.PUBLISH_ROLLBACK)
                    .chainCode(code)
                    .graphData(rollbackGraphData)
                    .chainData(rollbackChainData)
                    .totalExecutors(reloadResult.getSuccess())
                    .timestamp(System.currentTimeMillis())
                    .build();
            String rollbackBody;
            try {
                rollbackBody = MAPPER.writeValueAsString(rollbackEvent);
                // 只在成功的执行器上回滚
                for (ExecutorResult r : reloadResult.getResults()) {
                    if (r.isOk()) {
                        try {
                            ExecutorResult rollbackResult = proxyService.executeOnExecutorUrl(
                                    r.getUrl(), "PUT", "/api/chains/" + code + "/reload", rollbackBody);
                            if (rollbackResult.isOk()) {
                                log.info("回滚成功 executor={}", r.getUrl());
                            } else {
                                log.error("回滚失败 executor={} msg={}", r.getUrl(), rollbackResult.getMessage());
                            }
                        } catch (Exception re) {
                            log.error("回滚失败 executor={}", r.getUrl(), re);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("序列化回滚数据失败", e);
            }
        }

        // 缓存发布进度
        runtimeStateStore.savePublishProgress(code,
                reloadResult.getSuccess(), reloadResult.getTotal());

        if (reloadResult.isAllSuccess() && reloadResult.getTotal() > 0) {
            String statusPayload = "{\"status\":4,\"appCode\":\"" + appCode + "\"}";
            proxyService.broadcastToExecutors(appCode, "PUT", "/api/chains/" + code, statusPayload);
            log.info("链发布成功 code={} appCode={}", code, appCode);
        }

        // 同步图数据快照到采集器（不阻塞发布流程）
        try {
            ChainSnapshotSyncDTO snapshotSync = ChainSnapshotSyncDTO.builder()
                    .chainCode(code)
                    .graphData(graphData)
                    .appCode(appCode)
                    .tenantId(SecurityUtils.getCurrentTenantId())
                    .createdBy(SecurityUtils.getCurrentUsername())
                    .build();
            collectorClient.syncSnapshot(snapshotSync);
        } catch (Exception e) {
            log.warn("同步图数据快照失败，不影响发布 chainCode={}", code, e);
        }

        log.info("链发布完成 code={} appCode={} publishId={} success={}/{}",
                code, appCode, publishId, reloadResult.getSuccess(), reloadResult.getTotal());

        ObjectNode data = MAPPER.createObjectNode();
        data.put("code", reloadResult.isAllSuccess() && reloadResult.getTotal() > 0 ? 200 : 207);
        data.put("publishId", publishId);
        data.put("message", reloadResult.isAllSuccess()
                ? "发布成功"
                : String.format("发布完成: %d/%d 成功", reloadResult.getSuccess(), reloadResult.getTotal()));
        data.put("total", reloadResult.getTotal());
        data.put("success", reloadResult.getSuccess());
        ArrayNode details = data.putArray("details");
        for (ExecutorResult r : reloadResult.getResults()) {
            ObjectNode detail = MAPPER.createObjectNode();
            detail.put("url", r.getUrl());
            detail.put("ok", r.isOk());
            detail.put("message", r.getMessage());
            details.add(detail);
        }

        ObjectNode result = MAPPER.createObjectNode();
        result.put("code", 200);
        result.put("message", "success");
        result.set("data", data);
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"code\":500,\"message\":\"序列化发布结果失败\"}";
        }
    }

    @GetMapping("/{code}/versions")
    public String listVersions(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        return proxyService.getFromExecutor(appCode, "/api/chains/" + code + "/versions", null);
    }

    @PostMapping("/{code}/rollback/{version}")
    public String rollback(@PathVariable String code, @PathVariable Integer version,
                            @RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        requireAppPermission(appCode, "APP_ADMIN");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "POST",
                "/api/chains/" + code + "/rollback/" + version, enriched);
    }

    private String extractAppCode(String bodyJson) {
        try {
            JsonNode json = MAPPER.readTree(bodyJson);
            if (json.has("appCode") && !json.get("appCode").isNull()) {
                return json.get("appCode").asText();
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

    /**
     * 校验当前用户对指定 appCode 的访问权限，无权限则抛异常
     */
    private void requireAppPermission(String appCode, String requiredRole) {
        if (appCode == null || appCode.isBlank()) {
            return; // 无 appCode 的请求不做权限校验（系统级操作）
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        // 超管跳过
        if (SecurityUtils.isSuperAdmin(auth)) {
            return;
        }
        Long userId = SecurityUtils.getUserId(auth);
        if (userId == null || !permissionService.hasAppPermission(userId, appCode, requiredRole)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }
}
