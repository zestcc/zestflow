package com.zestflow.starter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * ZestFlow 一键引入自动配置
 * <p>
 * 聚合 Executor + Collector-JDBC 的自动装配，
 * 业务方引入 zestflow-starter 后无需额外配置。
 */
@AutoConfiguration
@Import({
        com.zestflow.executor.registry.ExecutorAutoConfig.class,
        com.zestflow.collector.jdbc.config.CollectorAutoConfig.class
})
public class ZestFlowAutoConfiguration {
}
