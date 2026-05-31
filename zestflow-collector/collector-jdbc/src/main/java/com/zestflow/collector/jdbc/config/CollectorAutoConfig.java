package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.jdbc.collector.JdbcEventCollector;
import com.zestflow.collector.jdbc.controller.CollectorController;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.registry.CollectorAdminClient;
import com.zestflow.collector.jdbc.registry.CollectorRegistrar;
import com.zestflow.collector.jdbc.registry.CollectorRegistryProperties;
import com.zestflow.collector.jdbc.service.JdbcEventQueryService;
import com.zestflow.collector.spi.EventCollector;
import com.zestflow.collector.spi.EventQueryService;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * Collector JDBC 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties({CollectorProperties.class, CollectorRegistryProperties.class})
@Import(CollectorDataSourceConfig.class)
public class CollectorAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public EventCollector jdbcEventCollector(ChainEventMapper chainEventMapper) {
        return new JdbcEventCollector(chainEventMapper);
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

    /**
     * REST 控制器仅在 spring-web 环境下生效
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    @ConditionalOnMissingBean
    public CollectorController collectorController(EventQueryService eventQueryService,
                                                    CollectorProperties properties) {
        return new CollectorController(eventQueryService, properties);
    }

    // ==================== 采集器注册 ====================

    /**
     * 注册用 RestTemplate（独立 Bean，避免与业务应用的 RestTemplate 冲突）
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate collectorRestTemplate() {
        return new RestTemplate();
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
