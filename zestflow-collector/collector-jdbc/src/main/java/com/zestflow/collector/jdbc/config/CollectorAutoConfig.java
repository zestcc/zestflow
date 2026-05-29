package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.jdbc.collector.JdbcEventCollector;
import com.zestflow.collector.jdbc.controller.CollectorController;
import com.zestflow.collector.jdbc.mapper.ChainEventMapper;
import com.zestflow.collector.jdbc.service.JdbcEventQueryService;
import com.zestflow.collector.spi.EventCollector;
import com.zestflow.collector.spi.EventQueryService;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Collector JDBC 自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(CollectorProperties.class)
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
}
