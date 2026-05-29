package com.zestflow.executor.registry;

import com.zestflow.collector.spi.EventCollector;
import com.zestflow.executor.event.AsyncEventPublisher;
import com.zestflow.executor.event.AsyncEventPublisher.AsyncPublisherConfig;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.server.ExecutorServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorAutoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties,
                                          java.util.Optional<EventPublisher> eventPublisher) {
        return new ExecutorServer(properties.getPort(), eventPublisher.orElse(null));
    }

    @Bean
    public RestTemplate zestflowRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public AdminClient adminClient(RestTemplate restTemplate, ExecutorProperties properties) {
        return new AdminClient(restTemplate, properties);
    }

    @Bean
    public ExecutorRegistrar executorRegistrar(AdminClient adminClient,
                                               ExecutorProperties properties,
                                               ExecutorServer executorServer,
                                               Environment environment) {
        return new ExecutorRegistrar(adminClient, properties, executorServer, environment);
    }

    /**
     * 异步事件发布器 — 仅在有 EventCollector bean 时创建
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(EventCollector.class)
    public EventPublisher eventPublisher(List<EventCollector> collectors,
                                          ExecutorProperties properties) {
        AsyncPublisherConfig config = AsyncPublisherConfig.builder()
                .queueCapacity(properties.getEventQueueCapacity())
                .batchSize(properties.getEventBatchSize())
                .batchMaxWaitMs(properties.getEventBatchMaxWaitMs())
                .circuitBreakerThreshold(properties.getEventCircuitBreakerThreshold())
                .circuitBreakerCooldownMs(properties.getEventCircuitBreakerCooldownMs())
                .diskFallbackEnabled(properties.isEventDiskFallbackEnabled())
                .diskFallbackDir(properties.getEventDiskFallbackDir())
                .build();
        return new AsyncEventPublisher(collectors, config);
    }
}
