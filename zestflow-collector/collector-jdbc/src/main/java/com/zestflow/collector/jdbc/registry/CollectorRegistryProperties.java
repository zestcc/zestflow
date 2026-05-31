package com.zestflow.collector.jdbc.registry;

import com.zestflow.common.constant.RegistryConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采集器注册配置属性 — 对标 ExecutorProperties 的注册部分
 */
@Data
@ConfigurationProperties(prefix = "zestflow.collector.registry")
public class CollectorRegistryProperties {

    /** Admin 地址，逗号分隔（默认 http://localhost:8080） */
    private String adminAddresses = "http://localhost:8080";

    /** Admin 认证令牌 */
    private String accessToken = "";

    /** 采集器 Host（为空则自动探测） */
    private String host = "";

    /** 采集器 Port（默认 9998） */
    private int port = 9998;

    /** 心跳间隔（秒） */
    private int heartbeatInterval = RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;

    /** 超时（毫秒） */
    private int timeoutMs = RegistryConstants.DEFAULT_REGISTRY_TIMEOUT_MS;
}
