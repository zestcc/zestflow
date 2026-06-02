package com.zestflow.admin.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin 运行时状态配置 — 仅 {@link AdminDeployProperties#isCluster()} 多机模式下生效。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.admin.runtime-state")
public class AdminRuntimeStateProperties {

    /** Redis 键 TTL（秒），仅 cluster 模式使用，默认 5 分钟 */
    private long ttlSeconds = 300;
}
