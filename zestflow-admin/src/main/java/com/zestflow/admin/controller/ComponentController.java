package com.zestflow.admin.controller;

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
 * 元件列表 — 通过 HTTP 代理到具体 Executor 端的 ComponentScanner
 */
@RestController
@RequestMapping("/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ExecutorProxyService proxyService;
    private final PermissionService permissionService;

    @GetMapping
    public String list(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String executorId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String componentType,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        if (appCode != null && !appCode.isBlank()) {
            requireAppPermission(appCode, "APP_VIEWER");
            String query = "?executorId=" + (executorId != null ? executorId : "")
                    + "&keyword=" + (keyword != null ? keyword : "")
                    + "&status=" + (status != null ? status : "")
                    + "&componentType=" + (componentType != null ? componentType : "")
                    + "&page=" + page + "&size=" + size;
            return proxyService.getFromExecutor(appCode, "/api/components", query);
        }
        return "{\"records\":[],\"total\":0,\"current\":1,\"size\":10}";
    }

    @GetMapping("/stats")
    public String stats(@RequestParam String appCode) {
        requireAppPermission(appCode, "APP_VIEWER");
        String baseUrl = proxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return "{\"total\":0,\"active\":0,\"offline\":0}";
        }
        String json = proxyService.getDirectFromUrl(baseUrl + "/api/components", "?page=1&size=9999");
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            int total = root.has("total") ? root.get("total").asInt() : 0;
            int active = 0;
            if (root.has("records")) {
                for (com.fasterxml.jackson.databind.JsonNode rec : root.get("records")) {
                    if (rec.has("status") && rec.get("status").asInt() == 1) active++;
                }
            }
            int offline = total - active;
            return "{\"total\":" + total + ",\"active\":" + active + ",\"offline\":" + offline + "}";
        } catch (Exception e) {
            return "{\"total\":0,\"active\":0,\"offline\":0}";
        }
    }

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
