package com.zestflow.admin.controller;

import com.zestflow.admin.client.ExecutorProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 元件列表 — 通过 HTTP 代理到具体 Executor 端的 ComponentScanner
 */
@RestController
@RequestMapping("/components")
@RequiredArgsConstructor
public class ComponentController {

    private final ExecutorProxyService proxyService;

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
        String baseUrl = proxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return "{\"total\":0,\"active\":0,\"offline\":0}";
        }
        String json = proxyService.getDirectFromUrl(baseUrl + "/api/components", "?page=1&size=9999");
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            int total = root.has("total") ? root.get("total").asInt() : 0;
            // 统计活跃数
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
}
