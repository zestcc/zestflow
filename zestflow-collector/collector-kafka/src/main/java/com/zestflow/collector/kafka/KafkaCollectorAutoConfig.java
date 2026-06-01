package com.zestflow.collector.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.spi.EventCollector;
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
@EnableConfigurationProperties(KafkaCollectorAutoConfig.KafkaCollectorProperties.class)
public class KafkaCollectorAutoConfig {

    @Bean
    @ConditionalOnMissingBean(name = "kafkaEventCollector")
    @ConditionalOnProperty(prefix = "zestflow.collector.kafka", name = "topic")
    public EventCollector kafkaEventCollector(KafkaTemplate<String, String> kafkaTemplate,
                                               KafkaCollectorProperties properties,
                                               ObjectMapper objectMapper) {
        return new KafkaEventCollector(kafkaTemplate, properties.getTopic(), objectMapper);
    }

    @ConfigurationProperties(prefix = "zestflow.collector.kafka")
    public static class KafkaCollectorProperties {
        /** Kafka Topic 名称 */
        private String topic = "zestflow-events";

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }
}
