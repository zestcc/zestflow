package com.zestflow.executor.registry;

import com.zestflow.common.spi.schedule.ScheduleDriver;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.event.AsyncEventPublisher;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.event.ExecutorEventProperties;
import com.zestflow.executor.event.SyncEventPublisher;
import com.zestflow.executor.chain.ChainDeclarationRegistry;
import com.zestflow.executor.chain.ChainDeclarationScanner;
import com.zestflow.executor.chain.ChainDeclarationSyncService;
import com.zestflow.executor.chain.ChainKeyResolver;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.ChainReloadMonitor;
import com.zestflow.executor.chain.ChainValidator;
import com.zestflow.executor.chain.ExecutorChainProperties;
import com.zestflow.executor.config.ChainRouteWebConfig;
import com.zestflow.executor.config.ExecutorSchedulingConfig;
import com.zestflow.executor.controller.ExecutionController;
import com.zestflow.executor.http.ChainErrorHandlerInvoker;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.http.ChainExecutionExceptionAdvice;
import com.zestflow.executor.http.ChainGateway;
import com.zestflow.executor.route.ChainRouteRegistry;
import com.zestflow.executor.engine.*;
import com.zestflow.executor.fallback.FallbackStrategy;
import com.zestflow.executor.fallback.DefaultFallbackStrategy;
import com.zestflow.executor.interceptor.*;
import com.zestflow.executor.lifecycle.*;
import com.zestflow.executor.param.ParamConverterRegistry;
import com.zestflow.executor.param.resolver.ContextTypeResolver;
import com.zestflow.executor.param.resolver.ParameterNameResolver;
import com.zestflow.executor.param.resolver.ParameterResolver;
import com.zestflow.executor.param.resolver.ZestFailureParameterResolver;
import com.zestflow.executor.param.resolver.ZestParamResolver;
import com.zestflow.executor.param.resolver.ZestResultParameterResolver;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.chain.ChainRepository;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.ai.ExecutorChainAiService;
import com.zestflow.executor.server.ExecutorServer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.zestflow.executor.config.ScheduleServerConfig;
import com.zestflow.executor.schedule.*;
import com.zestflow.executor.schedule.external.ExternalScheduleDriver;
import com.zestflow.executor.schedule.external.XxlJobScheduleConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import com.zestflow.executor.config.ExecutorProductionGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import java.util.List;

import com.zestflow.collector.http.ZestFlowHttpClient;
import com.zestflow.collector.remote.HttpRemoteEventCollector;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({ExecutorProperties.class, ExecutorChainProperties.class, ExecutorEventProperties.class,
        ExecutorScheduleProperties.class})
@Import({ExecutorSchedulingConfig.class, ChainRouteWebConfig.class, ScheduleServerConfig.class, XxlJobScheduleConfiguration.class})
public class ExecutorAutoConfig {

    @Bean
    public ExecutionIdempotencyGuard executionIdempotencyGuard() {
        return new ExecutionIdempotencyGuard();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutorChainAiService executorChainAiService(ExecutorProperties properties) {
        return new ExecutorChainAiService(properties);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ExecutorServer executorServer(ExecutorProperties properties,
                                          ChainExecutionEngine chainExecutionEngine,
                                          ChainRepository chainRepo,
                                          DesignRepository designRepo,
                                          ComponentScanner componentScanner,
                                          ChainLoader chainLoader,
                                          ExecutionIdempotencyGuard idempotencyGuard,
                                          ExecutorChainAiService chainAiService) {
        ExecutorServer server = new ExecutorServer(properties.getPort(), chainExecutionEngine,
                chainRepo, designRepo, componentScanner, chainLoader,
                properties.getAccessToken(), properties, idempotencyGuard);
        server.setChainAiService(chainAiService);
        return server;
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor", name = "execute-endpoint-enabled",
            havingValue = "true", matchIfMissing = false)
    public ExecutionController executionController(ChainExecuteFacade chainExecuteFacade) {
        return new ExecutionController(chainExecuteFacade);
    }

    @Bean
    public ChainErrorHandlerInvoker chainErrorHandlerInvoker(ComponentScanner componentScanner,
                                                              LifecycleExecutor lifecycleExecutor) {
        return new ChainErrorHandlerInvoker(componentScanner, lifecycleExecutor);
    }

    @Bean
    public ChainExecuteFacade chainExecuteFacade(ChainExecutionEngine chainExecutionEngine,
                                                  ChainManager chainManager,
                                                  ExecutionIdempotencyGuard idempotencyGuard,
                                                  ExecutorProperties properties,
                                                  ChainErrorHandlerInvoker errorHandlerInvoker,
                                                  ChainKeyResolver chainKeyResolver) {
        return new ChainExecuteFacade(chainExecutionEngine, chainManager, idempotencyGuard,
                properties, errorHandlerInvoker, chainKeyResolver);
    }

    @Bean
    public ChainGateway chainGateway(ChainExecuteFacade chainExecuteFacade) {
        return new ChainGateway(chainExecuteFacade);
    }

    @Bean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
    public ChainExecutionExceptionAdvice chainExecutionExceptionAdvice() {
        return new ChainExecutionExceptionAdvice();
    }

    @Bean
    public ZestFlowHttpClient zestflowAdminHttpClient(ExecutorProperties properties) {
        return new ZestFlowHttpClient(properties.getTimeoutMs());
    }

    @Bean
    public AdminClient adminClient(ZestFlowHttpClient zestflowAdminHttpClient,
                                    ExecutorProperties properties) {
        return new AdminClient(zestflowAdminHttpClient, properties);
    }

    @Bean
    public ExecutorRegistrar executorRegistrar(AdminClient adminClient,
                                               ExecutorProperties properties,
                                               ExecutorServer executorServer,
                                               Environment environment,
                                               ComponentScanner componentScanner,
                                               ChainDeclarationRegistry chainDeclarationRegistry) {
        return new ExecutorRegistrar(adminClient, properties, executorServer, environment,
                componentScanner, chainDeclarationRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor.chain", name = "auto-reload",
            havingValue = "true", matchIfMissing = true)
    public ChainReloadMonitor chainReloadMonitor(ChainRepository chainRepo,
                                                  DesignRepository designRepo,
                                                  ChainManager chainManager,
                                                  ChainLoader chainLoader) {
        return new ChainReloadMonitor(chainRepo, designRepo, chainManager, chainLoader);
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
    public ChainDeclarationRegistry chainDeclarationRegistry() {
        return new ChainDeclarationRegistry();
    }

    @Bean
    public ChainDeclarationScanner chainDeclarationScanner() {
        return new ChainDeclarationScanner();
    }

    @Bean
    public ChainKeyResolver chainKeyResolver(ChainRepository chainRepo, ExecutorProperties executorProperties) {
        return new ChainKeyResolver(chainRepo, executorProperties);
    }

    @Bean
    public ChainDeclarationSyncService chainDeclarationSyncService(
            ChainDeclarationScanner declarationScanner,
            ChainDeclarationRegistry declarationRegistry,
            ChainRepository chainRepository,
            ExecutorChainProperties chainProperties,
            ExecutorProperties executorProperties) {
        return new ChainDeclarationSyncService(declarationScanner, declarationRegistry,
                chainRepository, chainProperties, executorProperties);
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
                                   AdminClient adminClient,
                                   ExecutorProperties executorProperties,
                                   ObjectProvider<ChainRouteRegistry> chainRouteRegistryProvider) {
        return new ChainLoader(chainManager, componentScanner,
                chainValidator, chainDefinitionBuilder, chainRepo, designRepo, nodeRunner, adminClient,
                executorProperties, chainRouteRegistryProvider);
    }

    // ==================== 参数解析器 ====================

    @Bean
    public ZestResultParameterResolver zestResultParameterResolver(ParamConverterRegistry registry) {
        return new ZestResultParameterResolver(registry);
    }

    @Bean
    public ZestFailureParameterResolver zestFailureParameterResolver() {
        return new ZestFailureParameterResolver();
    }

    @Bean
    public ZestParamResolver zestParamResolver(ParamConverterRegistry registry) {
        return new ZestParamResolver(registry);
    }

    @Bean
    public ParameterNameResolver parameterNameResolver(ParamConverterRegistry registry) {
        return new ParameterNameResolver(registry);
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

    /**
     * Spring 销毁顺序：ExecutorServer → chainExecutionEngine → asyncEventPublisher
     * （依赖链创建顺序的逆序），保证先停接入、再等执行、最后 drain 事件队列。
     */
    @Bean(destroyMethod = "destroy")
    @ConditionalOnBean(EventCollector.class)
    @ConditionalOnProperty(prefix = "zestflow.executor.event", name = "async-enabled",
            havingValue = "true", matchIfMissing = true)
    public AsyncEventPublisher asyncEventPublisher(EventCollector eventCollector,
                                                    ExecutorEventProperties eventProperties) {
        return new AsyncEventPublisher(eventCollector, eventProperties.toSettings(),
                eventProperties.getOfferTimeoutMs());
    }

    @Bean
    @ConditionalOnMissingBean(AsyncEventPublisher.class)
    @ConditionalOnBean(EventCollector.class)
    public EventPublisher syncEventPublisher(EventCollector eventCollector) {
        return new SyncEventPublisher(eventCollector);
    }

    @Bean
    @ConditionalOnMissingBean(EventCollector.class)
    @ConditionalOnProperty(prefix = "zestflow.executor.event", name = "remote-collector-url")
    public EventCollector remoteHttpEventCollector(ZestFlowHttpClient zestflowAdminHttpClient,
                                                    ExecutorEventProperties eventProperties) {
        return new HttpRemoteEventCollector(
                zestflowAdminHttpClient,
                eventProperties.getRemoteCollectorUrl(),
                eventProperties.getRemoteCollectorToken());
    }

    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher noopEventPublisher() {
        log.warn("未检测到 EventCollector：链执行事件不会写入 chain_event。"
                + "请引入 zestflow-starter/collector-jdbc，或配置 zestflow.executor.event.remote-collector-url");
        return EventPublisher.noop();
    }

    @Bean
    public NodeRunner nodeRunner(ComponentScanner componentScanner,
                                 EventPublisher eventPublisher,
                                 InterceptorChain interceptorChain,
                                 LifecycleExecutor lifecycleExecutor,
                                 RetryExecutor retryExecutor,
                                 ChainManager chainManager,
                                 ExecutorProperties properties) {
        return new NodeRunner(componentScanner, eventPublisher,
                interceptorChain, lifecycleExecutor, retryExecutor, chainManager, properties);
    }

    @Bean
    public ChainTransactionExecutor chainTransactionExecutor(
            @Autowired(required = false) @Qualifier("executorTransactionManager")
            PlatformTransactionManager transactionManager) {
        return transactionManager != null
                ? new ChainTransactionExecutor(transactionManager)
                : ChainTransactionExecutor.noop();
    }

    @Bean(destroyMethod = "destroy")
    public DefaultChainExecutionEngine chainExecutionEngine(ChainManager chainManager,
                                                             DagSorter dagSorter,
                                                             NodeRunner nodeRunner,
                                                             ChainInstanceManager instanceManager,
                                                             EventPublisher eventPublisher,
                                                             InterceptorChain interceptorChain,
                                                             ExecutorProperties properties,
                                                             ChainLoader chainLoader,
                                                             ChainTransactionExecutor chainTransactionExecutor,
                                                             ChainKeyResolver chainKeyResolver) {
        DefaultChainExecutionEngine engine = new DefaultChainExecutionEngine(chainManager, dagSorter, nodeRunner,
                instanceManager, eventPublisher, interceptorChain, properties, chainTransactionExecutor);
        engine.setChainLoader(chainLoader);
        engine.setChainKeyResolver(chainKeyResolver);
        nodeRunner.setChainExecutionEngine(engine);
        return engine;
    }

    // ==================== 拦截器 ====================

    @Bean
    public LoggingInterceptor loggingInterceptor() {
        return new LoggingInterceptor();
    }

    @Bean
    public MetricsInterceptor metricsInterceptor(ObjectProvider<ChainMetricsSink> metricsSinkProvider) {
        return new MetricsInterceptor(metricsSinkProvider.getIfAvailable());
    }

    // Micrometer 绑定见 {@link com.zestflow.executor.metrics.ExecutorMicrometerAutoConfiguration}

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
    public ChainRuntimeRegistrar chainRuntimeRegistrar(
            @Qualifier("executorJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            ExecutorProperties properties) {
        return new ChainRuntimeRegistrar(jdbcTemplate, properties, properties.getTenantId());
    }

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

    @Bean
    public ScheduleRepository scheduleRepository(
            @Qualifier("executorJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            ExecutorProperties properties) {
        return new ScheduleRepository(jdbcTemplate, properties.getTenantId(), properties.getAppCode());
    }

    @Bean
    public ScheduleTriggerService scheduleTriggerService(ScheduleRepository scheduleRepository,
                                                         ChainExecuteFacade chainExecuteFacade,
                                                         ExecutorProperties properties) {
        return new ScheduleTriggerService(scheduleRepository, chainExecuteFacade, properties);
    }

    @Bean
    public ScheduleRouteHandler scheduleRouteHandler(ScheduleRepository scheduleRepository,
                                                     ScheduleTriggerService scheduleTriggerService) {
        return new ScheduleRouteHandler(scheduleRepository, scheduleTriggerService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor.schedule", name = "driver", havingValue = "noop")
    public NoopScheduleDriver noopScheduleDriver() {
        return new NoopScheduleDriver();
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.executor.schedule", name = "driver", havingValue = "embedded", matchIfMissing = true)
    public EmbeddedScheduleDriver embeddedScheduleDriver(ScheduleRepository scheduleRepository,
                                                         ScheduleTriggerService scheduleTriggerService,
                                                         ExecutorProperties executorProperties,
                                                         ExecutorScheduleProperties scheduleProperties) {
        return new EmbeddedScheduleDriver(scheduleRepository, scheduleTriggerService,
                executorProperties, scheduleProperties);
    }

    @Bean
    ApplicationRunner scheduleDriverStarter(java.util.Optional<EmbeddedScheduleDriver> embeddedScheduleDriver,
                                            java.util.Optional<ExternalScheduleDriver> externalScheduleDriver,
                                            java.util.Optional<NoopScheduleDriver> noopScheduleDriver) {
        return args -> {
            ScheduleDriver driver = embeddedScheduleDriver.<ScheduleDriver>map(d -> d)
                    .or(() -> externalScheduleDriver.map(d -> d))
                    .or(() -> noopScheduleDriver.map(d -> d))
                    .orElse(null);
            if (driver != null) {
                driver.start();
                log.info("ScheduleDriver 已启动 driverId={}", driver.driverId());
            }
        };
    }

    // ==================== 降级策略 ====================

    @Bean
    public FallbackStrategy defaultFallbackStrategy() {
        return new DefaultFallbackStrategy();
    }

    @Bean
    @Profile("prod")
    public ExecutorProductionGuard executorProductionGuard(ExecutorProperties properties) {
        return new ExecutorProductionGuard(properties);
    }
}
