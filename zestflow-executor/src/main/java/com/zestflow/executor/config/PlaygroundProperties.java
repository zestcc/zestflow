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

    /**
     * 已废弃：端点导入列表仅返回相对路径（如 /api/orders/...），执行统一经 Executor Netty 端口。
     */
    @Deprecated
    private String url;
}
