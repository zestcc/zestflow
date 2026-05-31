package com.zestflow.admin.playground;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.playground.PlaygroundProperties.SceneConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 试验场 — API 调试工具。
 * <p>
 * 仅当 {@code zestflow.playground.enabled=true} 时加载。
 * 提供场景执行、执行历史持久化、IP 审计能力。
 */
@RestController
@RequestMapping("/playground")
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class PlaygroundController {

    private final PlaygroundService playgroundService;
    private final PlaygroundProperties properties;
    private final PlaygroundRateLimiter rateLimiter;

    /**
     * 获取可用的演示场景列表
     */
    @GetMapping("/scenes")
    public ResponseEntity<?> listScenes() {
        List<Map<String, Object>> result = properties.getScenes().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.getId());
                    m.put("name", s.getName());
                    m.put("description", s.getDescription());
                    m.put("defaultParams", s.getDefaultParams());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    /**
     * 执行指定演示场景。
     * <p>
     * 支持两种请求体格式：
     * <pre>{@code
     * // 格式1：直接传参数
     * { "orderId": "ORD001", "amount": 99.9 }
     *
     * // 格式2：带自定义请求头
     * { "params": { "orderId": "ORD001" }, "headers": { "X-Trace-Id": "xxx" } }
     * }</pre>
     */
    @PostMapping("/execute/{sceneId}")
    public ResponseEntity<?> execute(
            @PathVariable String sceneId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        // 限流检查
        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("code", 429, "message", "请求过于频繁，请稍后再试"));
        }

        // 场景有效性检查
        SceneConfig scene = findScene(sceneId);
        if (scene == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", 404, "message", "场景不存在"));
        }

        // 解析请求：支持带 headers 的格式和纯 params 格式
        Map<String, Object> params;
        Map<String, String> headers;

        if (body != null && body.containsKey("params") && body.get("params") instanceof Map) {
            // 格式2：{ "params": {...}, "headers": {...} }
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyParams = (Map<String, Object>) body.get("params");
            params = bodyParams != null ? bodyParams : Map.of();

            @SuppressWarnings("unchecked")
            Map<String, String> bodyHeaders = (Map<String, String>) body.get("headers");
            headers = bodyHeaders != null ? bodyHeaders : Map.of();
        } else {
            // 格式1：{ "orderId": "ORD001", "amount": 99.9 }
            params = body != null ? body : Map.of();
            headers = Map.of();
        }

        // 解析客户端 IP
        String ip = resolveClientIp(request);

        Map<String, Object> result = playgroundService.executeScene(sceneId, params, headers, ip);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询执行历史列表
     */
    @GetMapping("/history")
    public ResponseEntity<?> listHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<PlaygroundLogVO> history = playgroundService.queryHistory(page, size);

        Map<String, Object> data = new HashMap<>();
        data.put("list", history.getRecords());
        data.put("total", history.getTotal());
        data.put("page", history.getCurrent());
        data.put("size", history.getSize());
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    /**
     * 查询单条执行详情
     */
    @GetMapping("/history/{id}")
    public ResponseEntity<?> getHistoryDetail(@PathVariable Long id) {
        PlaygroundLogVO vo = playgroundService.getHistoryDetail(id);
        if (vo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", 404, "message", "记录不存在"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", vo));
    }

    /**
     * 清理历史记录（仅删除指定 ID 之前的记录）
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory() {
        // 交给定时任务自动清理，不提供手动删除接口（安全考虑）
        return ResponseEntity.ok(Map.of("code", 200, "message", "由系统自动清理"));
    }

    private SceneConfig findScene(String sceneId) {
        for (SceneConfig s : properties.getScenes()) {
            if (s.getId().equals(sceneId)) {
                return s;
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
