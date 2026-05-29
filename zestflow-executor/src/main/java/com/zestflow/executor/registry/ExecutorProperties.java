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

    // ========== 事件采集配置（zestflow.executor.event.*） ==========

    /** 事件缓冲队列容量 */
    private int eventQueueCapacity = 8192;

    /** 批量提交大小 */
    private int eventBatchSize = 200;

    /** 批量提交最大等待时间（毫秒） */
    private int eventBatchMaxWaitMs = 500;

    /** 熔断阈值（连续失败次数，0 表示不熔断） */
    private int eventCircuitBreakerThreshold = 10;

    /** 熔断冷却时间（毫秒） */
    private int eventCircuitBreakerCooldownMs = 30_000;

    /** 是否启用磁盘降级 */
    private boolean eventDiskFallbackEnabled = false;

    /** 磁盘降级目录 */
    private String eventDiskFallbackDir = "./collector-fallback";
}
