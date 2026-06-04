package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.collector.jdbc.collector.JdbcEventCollector;
import com.zestflow.collector.jdbc.controller.CollectorController;
import com.zestflow.collector.jdbc.controller.GraphSnapshotController;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ChainEventPayloadMapper;
import com.zestflow.collector.jdbc.mapper.InvocationPayloadMapper;
import com.zestflow.collector.jdbc.mapper.ChainGraphSnapshotMapper;
import com.zestflow.collector.jdbc.registry.CollectorAdminClient;
import com.zestflow.collector.jdbc.registry.CollectorRegistrar;
import com.zestflow.collector.jdbc.registry.CollectorRegistryProperties;
import com.zestflow.collector.jdbc.server.CollectorServer;
import com.zestflow.collector.jdbc.metrics.CollectorMetricsProvider;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.collector.jdbc.service.JdbcEventQueryService;
import com.zestflow.collector.jdbc.service.JdbcInvocationPayloadService;
import com.zestflow.collector.spi.InvocationPayloadService;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.collector.spi.EventQueryService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import com.zestflow.collector.http.ZestFlowHttpClient;

/**
 * Collector JDBC 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties({CollectorProperties.class, CollectorRegistryProperties.class})
public class CollectorAutoConfig {

    /**
     * 异步事件采集器 — 包装 JdbcEventCollector，提供队列 + 批量 + 熔断
     * <p>
     * 配置 zestflow.collector.async-enabled=false 可关闭异步直接写入 DB
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(EventCollector.class)
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "async-enabled",
            havingValue = "true", matchIfMissing = true)
    public AsyncEventCollector asyncEventCollector(ChainEventMapper chainEventMapper,
                                                   ChainEventPayloadMapper chainEventPayloadMapper,
                                                   CollectorProperties properties) {
        JdbcEventCollector delegate = new JdbcEventCollector(chainEventMapper, chainEventPayloadMapper);
        return new AsyncEventCollector(delegate, properties.toAsyncSettings());
    }

    @Bean
    @ConditionalOnMissingBean(EventCollector.class)
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "async-enabled", havingValue = "false")
    public EventCollector jdbcEventCollector(ChainEventMapper chainEventMapper,
                                             ChainEventPayloadMapper chainEventPayloadMapper) {
        return new JdbcEventCollector(chainEventMapper, chainEventPayloadMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventQueryService jdbcEventQueryService(ChainEventMapper chainEventMapper,
                                                   ChainEventPayloadMapper chainEventPayloadMapper) {
        return new JdbcEventQueryService(chainEventMapper, chainEventPayloadMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public InvocationPayloadService jdbcInvocationPayloadService(InvocationPayloadMapper invocationPayloadMapper) {
        return new JdbcInvocationPayloadService(invocationPayloadMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CollectorMetricsProvider collectorMetricsProvider(EventCollector eventCollector,
                                                                CollectorProperties properties) {
        return new CollectorMetricsProvider(eventCollector, properties);
    }

    // ==================== Collector Netty 服务 ====================

    /**
     * Collector Netty HTTP 服务 — 独立端口，供 Admin 查询事件/轨迹/快照
     * <p>
     * 端口取自 zestflow.collector.registry.port，线程模型对标 ExecutorServer。
     * 当 zestflow.collector.netty-enabled=false 时降级为 Spring MVC Controller。
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "netty-enabled", havingValue = "true", matchIfMissing = true)
    public CollectorServer collectorServer(EventQueryService eventQueryService,
                                            InvocationPayloadService invocationPayloadService,
                                            ChainGraphSnapshotService snapshotService,
                                            CollectorRegistryProperties registryProperties,
                                            CollectorProperties collectorProperties,
                                            CollectorMetricsProvider metricsProvider) {
        int port = registryProperties.getPort() > 0 ? registryProperties.getPort() : 20650;
        return new CollectorServer(port, eventQueryService, invocationPayloadService, snapshotService,
                collectorProperties.getAccessToken(), metricsProvider);
    }

    // ==================== REST 控制器（Netty 禁用时降级）====================

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "netty-enabled", havingValue = "false")
    public CollectorController collectorController(EventQueryService eventQueryService,
                                                    InvocationPayloadService invocationPayloadService,
                                                    CollectorProperties properties,
                                                    CollectorMetricsProvider metricsProvider) {
        return new CollectorController(eventQueryService, invocationPayloadService, properties, metricsProvider);
    }

    // ==================== 图数据快照 ====================

    @Bean
    @ConditionalOnMissingBean
    public ChainGraphSnapshotService chainGraphSnapshotService(
            ChainGraphSnapshotMapper chainGraphSnapshotMapper,
            ChainEventMapper chainEventMapper) {
        return new ChainGraphSnapshotService(chainGraphSnapshotMapper, chainEventMapper);
    }

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "netty-enabled", havingValue = "false")
    public GraphSnapshotController graphSnapshotController(
            ChainGraphSnapshotService snapshotService,
            CollectorProperties properties) {
        return new GraphSnapshotController(snapshotService, properties);
    }

    // ==================== 采集器注册 ====================

    @Bean
    @ConditionalOnMissingBean(name = "collectorAdminHttpClient")
    public ZestFlowHttpClient collectorAdminHttpClient(CollectorProperties properties) {
        return new ZestFlowHttpClient(properties.getHttpTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean
    public CollectorAdminClient collectorAdminClient(ZestFlowHttpClient collectorAdminHttpClient,
                                                      CollectorRegistryProperties properties) {
        return new CollectorAdminClient(collectorAdminHttpClient, properties);
    }

    /**
     * 采集器注册管理器 — 实现 ApplicationRunner，启动时自动注册
     * 配置 zestflow.collector.registry.enabled=false 可关闭
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "zestflow.collector.registry", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CollectorRegistrar collectorRegistrar(CollectorAdminClient adminClient,
                                                  CollectorRegistryProperties properties,
                                                  Environment environment) {
        return new CollectorRegistrar(adminClient, properties, environment);
    }

    @Bean
    @Profile("prod")
    public CollectorProductionGuard collectorProductionGuard(CollectorProperties properties) {
        return new CollectorProductionGuard(properties);
    }
}
