package com.zestflow.executor.registry;

import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.chain.*;
import com.zestflow.executor.engine.*;
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
import com.zestflow.executor.controller.ExecutionController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(ExecutorProperties.class)
@Import(ExecutorDataSourceConfig.class)
public class ExecutorAutoConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties,
                                          ChainExecutionEngine chainExecutionEngine,
                                          ChainRepository chainRepo,
                                          DesignRepository designRepo,
                                          ComponentScanner componentScanner,
                                          ChainLoader chainLoader) {
        return new ExecutorServer(properties.getPort(), chainExecutionEngine,
                chainRepo, designRepo, componentScanner, chainLoader,
                properties.getAccessToken(), properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor", name = "execute-endpoint-enabled",
            havingValue = "true", matchIfMissing = false)
    public ExecutionController executionController(ChainExecutionEngine chainExecutionEngine,
                                                    ChainManager chainManager) {
        return new ExecutionController(chainExecutionEngine, chainManager);
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
    public AdminClient adminClient(@Qualifier("zestflowRestTemplate") RestTemplate restTemplate,
                                    ExecutorProperties properties) {
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
                                 java.util.Optional<EventCollector> eventCollector,
                                 InterceptorChain interceptorChain,
                                 LifecycleExecutor lifecycleExecutor,
                                 RetryExecutor retryExecutor,
                                 ChainManager chainManager,
                                 ExecutorProperties properties) {
        return new NodeRunner(componentScanner, eventCollector.orElse(null),
                interceptorChain, lifecycleExecutor, retryExecutor, chainManager, properties);
    }

    @Bean(destroyMethod = "destroy")
    public DefaultChainExecutionEngine chainExecutionEngine(ChainManager chainManager,
                                                             DagSorter dagSorter,
                                                             NodeRunner nodeRunner,
                                                             ChainInstanceManager instanceManager,
                                                             java.util.Optional<EventCollector> eventCollector,
                                                             InterceptorChain interceptorChain,
                                                             ExecutorProperties properties,
                                                             ChainLoader chainLoader) {
        DefaultChainExecutionEngine engine = new DefaultChainExecutionEngine(chainManager, dagSorter, nodeRunner,
                instanceManager, eventCollector.orElse(null), interceptorChain, properties);
        // setter 注入打破循环依赖：NodeRunner → ChainExecutionEngine, ChainExecutionEngine → ChainLoader
        engine.setChainLoader(chainLoader);
        nodeRunner.setChainExecutionEngine(engine);
        return engine;
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
    public ChainRepository chainRepository(@Qualifier("executorJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                           ExecutorProperties properties) {
        return new ChainRepository(jdbcTemplate, properties.getTenantId());
    }

    @Bean
    public DesignRepository designRepository(@Qualifier("executorJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                             ExecutorProperties properties) {
        return new DesignRepository(jdbcTemplate, properties.getTenantId());
    }

    // ==================== 降级策略 ====================

    @Bean
    public FallbackStrategy defaultFallbackStrategy() {
        return new DefaultFallbackStrategy();
    }
}
