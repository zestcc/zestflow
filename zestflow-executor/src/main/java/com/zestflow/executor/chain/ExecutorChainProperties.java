package com.zestflow.executor.chain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 链执行配置 — 绑定 {@code zestflow.executor.chain.*}（对标 LiteFlow 规则源轮询刷新）。
 */
@Data
@ConfigurationProperties(prefix = "zestflow.executor.chain")
public class ExecutorChainProperties {

    private int loadRetryTimes = 3;
    private long loadRetryIntervalMs = 5000;
    private boolean autoReload = true;
    private long reloadCheckIntervalMs = 60000;
    private int parallelThreshold = 3;
    private String dataDir = "./zestflow-data";
    private long defaultTimeoutMs = 60000;
    private int nodeDefaultRetryCount = 0;
    private long nodeDefaultRetryIntervalMs = 1000;

    /** 启动时同步 @ZestChain 声明为 DB 占位链（status=未设计） */
    private boolean declarationSyncEnabled = true;

    /** 应用仍声明 chain_key 时禁止删除对应链 */
    private boolean declarationDeleteGuardEnabled = true;
}
