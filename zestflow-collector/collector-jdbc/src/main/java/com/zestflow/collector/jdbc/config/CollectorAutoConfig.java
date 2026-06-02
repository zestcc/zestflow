package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.collector.jdbc.collector.JdbcEventCollector;
import com.zestflow.collector.jdbc.controller.CollectorController;
import com.zestflow.collector.jdbc.controller.GraphSnapshotController;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.mapper.ChainGraphSnapshotMapper;
import com.zestflow.collector.jdbc.registry.CollectorAdminClient;
import com.zestflow.collector.jdbc.registry.CollectorRegistrar;
import com.zestflow.collector.jdbc.registry.CollectorRegistryProperties;
import com.zestflow.collector.jdbc.server.CollectorServer;
import com.zestflow.collector.jdbc.metrics.CollectorMetricsProvider;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.collector.jdbc.service.JdbcEventQueryService;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.collector.spi.EventQueryService;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.zestflow.collector.async.metrics.CollectorMetricsSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Collector JDBC 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties({CollectorProperties.class, CollectorRegistryProperties.class})
@Import(CollectorDataSourceConfig.class)
public class CollectorAutoConfig {

    /**
     * 异步事件采集器 — 包装 JdbcEventCollector，提供队列 + 批量 + 熔断
     * <p>
     * 配置 zestflow.collector.async-enabled=false 可关闭异步直接写入 DB
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean(EventCollector.class)
    public EventCollector asyncEventCollector(ChainEventMapper chainEventMapper,
                                               CollectorProperties properties,
                                               ObjectProvider<MeterRegistry> meterRegistry) {
        JdbcEventCollector delegate = new JdbcEventCollector(chainEventMapper);
        if (properties.isAsyncEnabled()) {
            AsyncEventCollector async = new AsyncEventCollector(delegate, properties.toAsyncSettings());
            meterRegistry.ifAvailable(registry -> CollectorMetricsSupport.bindIfAvailable(async, registry));
            return async;
        }
        return delegate;
    }

    @Bean
    @ConditionalOnMissingBean
    public EventQueryService jdbcEventQueryService(ChainEventMapper chainEventMapper) {
        return new JdbcEventQueryService(chainEventMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler myMetaObjectHandler() {
        return new MyMetaObjectHandler();
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
                                            ChainGraphSnapshotService snapshotService,
                                            CollectorRegistryProperties registryProperties,
                                            CollectorProperties collectorProperties,
                                            CollectorMetricsProvider metricsProvider) {
        int port = registryProperties.getPort() > 0 ? registryProperties.getPort() : 20650;
        return new CollectorServer(port, eventQueryService, snapshotService,
                collectorProperties.getAccessToken(), metricsProvider);
    }

    // ==================== REST 控制器（Netty 禁用时降级）====================

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "zestflow.collector", name = "netty-enabled", havingValue = "false")
    public CollectorController collectorController(EventQueryService eventQueryService,
                                                    CollectorProperties properties,
                                                    CollectorMetricsProvider metricsProvider) {
        return new CollectorController(eventQueryService, properties, metricsProvider);
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

    /**
     * 注册用 RestTemplate（独立 Bean，避免与业务应用的 RestTemplate 冲突）
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate collectorRestTemplate(CollectorProperties properties) {
        RestTemplate restTemplate = new RestTemplate();
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getHttpTimeoutMs());
        factory.setReadTimeout(properties.getHttpTimeoutMs());
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public CollectorAdminClient collectorAdminClient(RestTemplate collectorRestTemplate,
                                                      CollectorRegistryProperties properties) {
        return new CollectorAdminClient(collectorRestTemplate, properties);
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
}
