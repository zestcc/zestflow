package com.zestflow.admin.system;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统配置/特性查询 — 始终加载，不受开关影响
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final Environment environment;

    /**
     * 获取系统特性开关状态，前端据此控制菜单显隐
     */
    @GetMapping("/features")
    public Map<String, Object> getFeatures() {
        boolean playgroundEnabled = "true".equalsIgnoreCase(
                environment.getProperty("zestflow.playground.enabled", "false"));
        return Map.of("playground", Map.of("enabled", playgroundEnabled));
    }
}
