package com.zestflow.admin.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admin 部署模式 — 仅控制运行时状态（发布进度、链 sync）的存储后端。
 * <p>
 * 业务缓存由 {@code zestflow.admin.cache.type} 单独配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zestflow.admin")
public class AdminDeployProperties {

    /** 部署模式：standalone（单机，默认）/ cluster（多机 Admin 副本） */
    private String deployMode = "standalone";

    public void setDeployMode(String deployMode) {
        this.deployMode = AdminDeployModeConditions.normalizeDeployMode(deployMode);
    }

    public boolean isStandalone() {
        return "standalone".equals(deployMode);
    }

    public boolean isCluster() {
        return "cluster".equals(deployMode);
    }
}
