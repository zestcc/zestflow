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

    /** 控制器扫描包范围（空则不限制），如 ["com.zestflow.demo.controller"] */
    private List<String> scanPackages = Collections.emptyList();

    /**
     * 业务 API 对外基址（Tomcat / 网关），如 {@code http://127.0.0.1:8081} 或 {@code https://www.test.zestflow}。
     * <p>
     * 配置后：端点导入展示完整 URL，试验场业务调用走此通道；未配置则走 Executor Netty 端口（Admin 代理鉴权）。
     * 链执行 {@code /execute} 始终走 Netty。
     */
    private String url;
}
