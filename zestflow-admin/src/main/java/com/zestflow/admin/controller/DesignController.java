package com.zestflow.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 设计管理 — 所有数据通过 HTTP 代理到具体 Executor 端
 */
@RestController
@RequestMapping("/designs")
@RequiredArgsConstructor
public class DesignController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        return proxyService.getFromExecutor(moduleId, "/api/designs", query);
    }

    @GetMapping("/{code}")
    public String getByCode(@PathVariable String code, @RequestParam Long moduleId) {
        return proxyService.getFromExecutor(moduleId, "/api/designs/" + code, null);
    }

    @PostMapping
    public String create(@RequestBody String bodyJson) {
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(extractModuleId(bodyJson), "POST", "/api/designs", enriched);
    }

    @PutMapping("/{code}")
    public String update(@PathVariable String code, @RequestBody String bodyJson) {
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(extractModuleId(bodyJson), "PUT", "/api/designs/" + code, enriched);
    }

    @PutMapping("/{code}/graph")
    public String saveGraph(@PathVariable String code, @RequestBody String bodyJson) {
        Long moduleId = extractModuleId(bodyJson);
        if (moduleId == null) return "{\"code\":400,\"message\":\"缺少 moduleId\"}";
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(moduleId, "PUT", "/api/designs/" + code + "/graph", enriched);
    }

    @DeleteMapping("/{code}")
    public String delete(@PathVariable String code, @RequestParam Long moduleId) {
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(moduleId, "DELETE", "/api/designs/" + code + query, null);
    }

    @PutMapping("/{code}/status")
    public String toggleStatus(@PathVariable String code, @RequestParam Long moduleId) {
        String body = "{\"moduleId\":" + moduleId + "}";
        String enriched = injectUpdatedBy(body);
        return proxyService.executeOnExecutor(moduleId, "PUT", "/api/designs/" + code + "/status", enriched);
    }

    @GetMapping("/{code}/bindings")
    public String getBindings(@PathVariable String code, @RequestParam Long moduleId) {
        return proxyService.getFromExecutor(moduleId, "/api/designs/" + code + "/bindings", null);
    }

    @GetMapping("/{code}/bindable")
    public String getBindable(@PathVariable String code, @RequestParam Long moduleId) {
        return proxyService.getFromExecutor(moduleId, "/api/designs/" + code + "/bindable", null);
    }

    @PostMapping("/{code}/bindings")
    public String bind(@PathVariable String code, @RequestParam Long moduleId, @RequestBody String bodyJson) {
        String enriched = injectUpdatedBy(bodyJson);
        return proxyService.executeOnExecutor(moduleId, "POST", "/api/designs/" + code + "/bindings", enriched);
    }

    @DeleteMapping("/{code}/bindings/{chainCode}")
    public String unbind(@PathVariable String code, @PathVariable String chainCode, @RequestParam Long moduleId) {
        // unbind is DELETE without body — pass updatedBy as query param
        String username = com.zestflow.admin.util.SecurityUtils.getCurrentUsername();
        String query = username != null ? "?updatedBy=" + username : "";
        return proxyService.executeOnExecutor(moduleId, "DELETE",
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

    private Long extractModuleId(String bodyJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(bodyJson);
            if (json.has("moduleId") && !json.get("moduleId").isNull()) {
                return json.get("moduleId").asLong();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
