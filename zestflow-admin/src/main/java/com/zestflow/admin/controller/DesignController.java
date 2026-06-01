package com.zestflow.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.PermissionService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 设计管理 — 所有数据通过 HTTP 代理到具体 Executor 端
 */
@RestController
@RequestMapping("/api/designs")
@RequiredArgsConstructor
public class DesignController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorProxyService proxyService;
    private final PermissionService permissionService;

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
        return proxyService.getFromExecutor(appCode, "/api/designs", query);
    }

    @GetMapping("/{code}")
    public String getByCode(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        return proxyService.getFromExecutor(appCode, "/api/designs/" + code, null);
    }

    @PostMapping
    public String create(@RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "POST", "/api/designs", enriched);
    }

    @PutMapping("/{code}")
    public String update(@PathVariable String code, @RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "PUT", "/api/designs/" + code, enriched);
    }

    @PutMapping("/{code}/graph")
    public String saveGraph(@PathVariable String code, @RequestBody String bodyJson) {
        String appCode = extractAppCode(bodyJson);
        if (appCode == null || appCode.isBlank()) return "{\"code\":400,\"message\":\"缺少 appCode\"}";
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "PUT", "/api/designs/" + code + "/graph", enriched);
    }

    @DeleteMapping("/{code}")
    public String delete(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_ADMIN");
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(appCode, "DELETE", "/api/designs/" + code + query, null);
    }

    @PutMapping("/{code}/status")
    public String toggleStatus(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_EDITOR");
        String body = "{\"appCode\":\"" + appCode + "\"}";
        String enriched = injectUpdatedBy(body);
        return proxyService.executeOnExecutor(appCode, "PUT", "/api/designs/" + code + "/status", enriched);
    }

    @GetMapping("/{code}/bindings")
    public String getBindings(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        return proxyService.getFromExecutor(appCode, "/api/designs/" + code + "/bindings", null);
    }

    @GetMapping("/{code}/bindable")
    public String getBindable(@PathVariable String code, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        return proxyService.getFromExecutor(appCode, "/api/designs/" + code + "/bindable", null);
    }

    @PostMapping("/{code}/bindings")
    public String bind(@PathVariable String code, @RequestParam String appCode, @RequestBody String bodyJson) {
        requireAppPermission(appCode, "APP_EDITOR");
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(appCode, "POST", "/api/designs/" + code + "/bindings", enriched);
    }

    @DeleteMapping("/{code}/bindings/{chainCode}")
    public String unbind(@PathVariable String code, @PathVariable String chainCode, @RequestParam String appCode) {
        requireAppPermission(appCode, "APP_EDITOR");
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(appCode, "DELETE",
                "/api/designs/" + code + "/bindings/" + chainCode + query, null);
    }

    private String injectUpdatedBy(String bodyJson) {
        try {
            ObjectNode node = (ObjectNode) MAPPER.readTree(bodyJson);
            String username = SecurityUtils.getCurrentUsername();
            if (username != null) {
                node.put("updatedBy", username);
            }
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return bodyJson;
        }
    }

    private String extractAppCode(String bodyJson) {
        try {
            JsonNode json = new ObjectMapper().readTree(bodyJson);
            if (json.has("appCode") && !json.get("appCode").isNull()) {
                return json.get("appCode").asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 校验当前用户对指定 appCode 的访问权限
     */
    private void requireAppPermission(String appCode, String requiredRole) {
        if (appCode == null || appCode.isBlank()) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (SecurityUtils.isSuperAdmin(auth)) return;
        Long userId = SecurityUtils.getUserId(auth);
        if (userId == null || !permissionService.hasAppPermission(userId, appCode, requiredRole)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }
}
