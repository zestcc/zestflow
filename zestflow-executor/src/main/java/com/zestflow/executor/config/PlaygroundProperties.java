package com.zestflow.executor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.List;

/**
 * Playground 模块配置 — 端点扫描等正式功能配置
 */
@Data
@ConfigurationProperties(prefix = "zestflow.playground")
public class PlaygroundProperties {

    /** 控制器扫描包范围（空则不限制），如 ["com.zestflow.test.controller"] */
    private List<String> scanPackages = Collections.emptyList();

    /** 演示场应用的基础 URL（如 http://localhost:8081），导入端点时拼接完整路径 */
    private String url;
}
