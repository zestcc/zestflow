package com.zestflow.executor.registry;

import com.zestflow.collector.spi.EventCollector;
import com.zestflow.executor.chain.*;
import com.zestflow.executor.engine.*;
import com.zestflow.executor.event.AsyncEventPublisher;
import com.zestflow.executor.event.AsyncEventPublisher.AsyncPublisherConfig;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.fallback.FallbackStrategy;
import com.zestflow.executor.fallback.DefaultFallbackStrategy;
import com.zestflow.executor.interceptor.*;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.param.ParamConverterRegistry;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.server.ExecutorServer;
import com.zestflow.executor.server.ServerHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorAutoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties,
                                          java.util.Optional<EventPublisher> eventPublisher,
                                          ChainExecutionEngine chainExecutionEngine) {
        return new ExecutorServer(properties.getPort(), chainExecutionEngine, eventPublisher.orElse(null));
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

    // ==================== 事件发布 ====================

    /**
     * 异步事件发布器 — 仅在有 EventCollector bean 时创建
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(EventCollector.class)
    public EventPublisher asyncEventPublisher(List<EventCollector> collectors,
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

    /**
     * 无 Collector 时的兜底事件发布器（空实现，不丢失业务调用）
     */
    @Bean
    @ConditionalOnMissingBean(EventCollector.class)
    public EventPublisher noOpEventPublisher() {
        return event -> {};
    }

    // ==================== 组件扫描 ====================

    @Bean
    public ComponentScanner componentScanner() {
        return new ComponentScanner();
    }

    // ==================== 参数转换器 ====================

    @Bean
    public ParamConverterRegistry paramConverterRegistry() {
        return new ParamConverterRegistry();
    }

    // ==================== 链管理 ====================

    @Bean
    public ChainManager chainManager() {
        return new ChainManager();
    }

    @Bean
    public ChainValidator chainValidator(ComponentScanner componentScanner) {
        return new ChainValidator(componentScanner);
    }

    @Bean
    public ChainDefinitionBuilder chainDefinitionBuilder(ComponentScanner componentScanner) {
        return new ChainDefinitionBuilder(componentScanner);
    }

    @Bean
    public ChainLoader chainLoader(AdminClient adminClient,
                                   ChainManager chainManager,
                                   ComponentScanner componentScanner,
                                   ChainValidator chainValidator,
                                   ChainDefinitionBuilder chainDefinitionBuilder,
                                   ExecutorProperties properties) {
        return new ChainLoader(adminClient, chainManager, componentScanner,
                chainValidator, chainDefinitionBuilder, properties);
    }

    // ==================== 执行引擎 ====================

    @Bean
    public DagSorter dagSorter() {
        return new DagSorter();
    }

    @Bean
    public ChainInstanceManager chainInstanceManager() {
        return new ChainInstanceManager();
    }

    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor();
    }

    @Bean
    public LifecycleExecutor lifecycleExecutor(ComponentScanner componentScanner,
                                               ParamConverterRegistry paramConverterRegistry) {
        return new LifecycleExecutor(componentScanner, paramConverterRegistry);
    }

    @Bean
    public NodeRunner nodeRunner(ComponentScanner componentScanner,
                                 EventPublisher eventPublisher,
                                 InterceptorChain interceptorChain,
                                 LifecycleExecutor lifecycleExecutor,
                                 RetryExecutor retryExecutor) {
        return new NodeRunner(componentScanner, eventPublisher,
                interceptorChain, lifecycleExecutor, retryExecutor);
    }

    @Bean
    public DefaultChainExecutionEngine chainExecutionEngine(ChainManager chainManager,
                                                             DagSorter dagSorter,
                                                             NodeRunner nodeRunner,
                                                             ChainInstanceManager instanceManager,
                                                             EventPublisher eventPublisher,
                                                             InterceptorChain interceptorChain,
                                                             ExecutorProperties properties) {
        return new DefaultChainExecutionEngine(chainManager, dagSorter, nodeRunner,
                instanceManager, eventPublisher, interceptorChain, properties);
    }

    // ==================== 拦截器 ====================

    @Bean
    public LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    @Bean
    public MetricsInterceptor metricsInterceptor() {
        return new MetricsInterceptor();
    }

    @Bean
    public AuthInterceptor authInterceptor(ExecutorProperties properties) {
        return new AuthInterceptor(properties.getAccessToken());
    }

    @Bean
    public InterceptorChain interceptorChain(List<ChainInterceptor> chainInterceptors,
                                              List<NodeInterceptor> nodeInterceptors) {
        InterceptorChain chain = new InterceptorChain();
        for (ChainInterceptor ci : chainInterceptors) {
            chain.addChainInterceptor(ci);
        }
        for (NodeInterceptor ni : nodeInterceptors) {
            chain.addNodeInterceptor(ni);
        }
        return chain;
    }

    // ==================== ServerHandler ====================

    @Bean
    public ServerHandler serverHandler(ChainExecutionEngine chainExecutionEngine,
                                        java.util.Optional<EventPublisher> eventPublisher) {
        ServerHandler handler = new ServerHandler(chainExecutionEngine);
        eventPublisher.ifPresent(handler::setEventPublisher);
        return handler;
    }

    // ==================== 降级策略 ====================

    @Bean
    public FallbackStrategy defaultFallbackStrategy() {
        return new DefaultFallbackStrategy();
    }
}
