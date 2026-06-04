package com.zestflow.collector.async.metrics;

import com.zestflow.collector.async.AsyncEventCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * 采集器 Micrometer 可选自动配置 — 与 transport 模块的 {@code *CollectorAutoConfig} 分离，
 * 嵌入模式未引入 micrometer-core 时不加载本类。
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class CollectorMicrometerAutoConfiguration {

    @Bean
    static AsyncEventCollectorMetricsPostProcessor asyncEventCollectorMetricsPostProcessor(
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new AsyncEventCollectorMetricsPostProcessor(meterRegistryProvider);
    }

    static final class AsyncEventCollectorMetricsPostProcessor implements BeanPostProcessor {

        private final ObjectProvider<MeterRegistry> meterRegistryProvider;

        AsyncEventCollectorMetricsPostProcessor(ObjectProvider<MeterRegistry> meterRegistryProvider) {
            this.meterRegistryProvider = meterRegistryProvider;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof AsyncEventCollector async) {
                meterRegistryProvider.ifAvailable(registry ->
                        CollectorMetricsSupport.bindIfAvailable(async, registry));
            }
            return bean;
        }
    }
}
