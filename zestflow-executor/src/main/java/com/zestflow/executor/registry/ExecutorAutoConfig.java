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
import com.zestflow.executor.lifecycle.*;
import com.zestflow.executor.param.ParamConverterRegistry;
import com.zestflow.executor.param.resolver.ContextTypeResolver;
import com.zestflow.executor.param.resolver.ParameterResolver;
import com.zestflow.executor.param.resolver.ZestParamResolver;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.chain.ChainRepository;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.server.ExecutorServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
public class ExecutorAutoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties,
                                          java.util.Optional<EventPublisher> eventPublisher,
                                          ChainExecutionEngine chainExecutionEngine,
                                          ChainRepository chainRepo,
                                          DesignRepository designRepo,
                                          ComponentScanner componentScanner,
                                          ChainLoader chainLoader) {
        return new ExecutorServer(properties.getPort(), chainExecutionEngine, eventPublisher.orElse(null),
                chainRepo, designRepo, componentScanner, chainLoader);
    }

    @Bean
    public RestTemplate zestflowRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // 默认 StringHttpMessageConverter 用 ISO-8859-1，中文会变 ????
        restTemplate.getMessageConverters().stream()
                .filter(c -> c instanceof org.springframework.http.converter.StringHttpMessageConverter)
                .forEach(c -> ((org.springframework.http.converter.StringHttpMessageConverter) c)
                        .setDefaultCharset(StandardCharsets.UTF_8));
        return restTemplate;
    }

    @Bean
    public AdminClient adminClient(RestTemplate restTemplate, ExecutorProperties properties) {
        return new AdminClient(restTemplate, properties);
    }

    @Bean
    public ExecutorRegistrar executorRegistrar(AdminClient adminClient,
                                               ExecutorProperties properties,
                                               ExecutorServer executorServer,
                                               Environment environment,
                                               ComponentScanner componentScanner) {
        return new ExecutorRegistrar(adminClient, properties, executorServer, environment, componentScanner);
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
    public ChainLoader chainLoader(ChainManager chainManager,
                                   ComponentScanner componentScanner,
                                   ChainValidator chainValidator,
                                   ChainDefinitionBuilder chainDefinitionBuilder,
                                   ChainRepository chainRepo,
                                   DesignRepository designRepo,
                                   NodeRunner nodeRunner,
                                   AdminClient adminClient) {
        return new ChainLoader(chainManager, componentScanner,
                chainValidator, chainDefinitionBuilder, chainRepo, designRepo, nodeRunner, adminClient);
    }

    // ==================== 参数解析器 ====================

    @Bean
    public ZestParamResolver zestParamResolver(ParamConverterRegistry registry) {
        return new ZestParamResolver(registry);
    }

    @Bean
    public ContextTypeResolver contextTypeResolver() {
        return new ContextTypeResolver();
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
                                               List<ParameterResolver> resolvers) {
        return new LifecycleExecutor(componentScanner, resolvers);
    }

    @Bean
    public ParamValidator paramValidator(org.springframework.beans.factory.ObjectProvider<jakarta.validation.Validator> validatorProvider) {
        return new ParamValidator(validatorProvider.getIfAvailable());
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

    // ==================== 数据访问 ====================

    @Bean
    public ChainRepository chainRepository(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new ChainRepository(jdbcTemplate);
    }

    @Bean
    public DesignRepository designRepository(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new DesignRepository(jdbcTemplate);
    }

    // ==================== 降级策略 ====================

    @Bean
    public FallbackStrategy defaultFallbackStrategy() {
        return new DefaultFallbackStrategy();
    }
}
