package com.zestflow.executor.registry;

import com.zestflow.common.constant.RegistryConstants;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zestflow.executor")
public class ExecutorProperties {

    /** 模块编码（为空则使用 spring.application.name） */
    private String moduleCode;

    /** 模块名称（为空则默认等于 moduleCode） */
    private String moduleName;

    /** Admin 服务地址，多个用逗号分隔，如 http://localhost:8080 */
    private String adminAddresses = "http://localhost:8080";

    /** 认证令牌（与 Admin 端共享） */
    private String accessToken;

    /** 心跳间隔（秒） */
    private int heartbeatInterval = RegistryConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;

    /** 执行器暴露的 Host（为空则自动探测本机 IP） */
    private String host;

    /** 执行器暴露的 Port */
    private int port = 9999;

    /** 注册/心跳请求超时（毫秒） */
    private int timeoutMs = RegistryConstants.DEFAULT_REGISTRY_TIMEOUT_MS;
}
