package com.zestflow.collector.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.collector.async.CollectorAsyncProperties;
import com.zestflow.collector.async.metrics.CollectorMetricsSupport;
import com.zestflow.common.spi.EventCollector;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka 采集器自动配置
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties({KafkaCollectorAutoConfig.KafkaCollectorProperties.class, CollectorAsyncProperties.class})
public class KafkaCollectorAutoConfig {

    @Bean
    @ConditionalOnMissingBean(name = "kafkaEventCollector")
    @ConditionalOnProperty(prefix = "zestflow.collector.kafka", name = "topic")
    public EventCollector kafkaEventCollector(KafkaTemplate<String, String> kafkaTemplate,
                                               KafkaCollectorProperties properties,
                                               CollectorAsyncProperties asyncProperties,
                                               ObjectMapper objectMapper,
                                               ObjectProvider<MeterRegistry> meterRegistry) {
        EventCollector delegate = new KafkaEventCollector(kafkaTemplate, properties.getTopic(), objectMapper);
        if (asyncProperties.isAsyncEnabled()) {
            AsyncEventCollector async = new AsyncEventCollector(delegate, asyncProperties.toSettings());
            meterRegistry.ifAvailable(registry -> CollectorMetricsSupport.bindIfAvailable(async, registry));
            return async;
        }
        return delegate;
    }

    @ConfigurationProperties(prefix = "zestflow.collector.kafka")
    public static class KafkaCollectorProperties {
        /** Kafka Topic 名称 */
        private String topic = "zestflow-events";

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }
}
