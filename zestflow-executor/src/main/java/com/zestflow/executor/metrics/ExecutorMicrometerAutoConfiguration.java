package com.zestflow.executor.metrics;

import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.interceptor.ChainMetricsSink;
import com.zestflow.executor.interceptor.MetricsInterceptor;
import com.zestflow.executor.metrics.ExecutorMicrometerBinder;
import com.zestflow.executor.metrics.MicrometerChainMetricsSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Micrometer 可选自动配置 — 与 {@link com.zestflow.executor.registry.ExecutorAutoConfig} 分离，
 * 嵌入模式未引入 micrometer-core 时不加载本类。
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class ExecutorMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChainMetricsSink.class)
    public ChainMetricsSink micrometerChainMetricsSink(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new MicrometerChainMetricsSink(meterRegistryProvider.getIfAvailable());
    }

    @Bean
    public MeterBinder executorMicrometerBinder(MetricsInterceptor metricsInterceptor,
                                                 ChainManager chainManager) {
        return new ExecutorMicrometerBinder(metricsInterceptor, chainManager);
    }
}
