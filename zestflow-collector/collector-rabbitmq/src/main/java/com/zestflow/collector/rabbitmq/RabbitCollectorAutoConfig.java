package com.zestflow.collector.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.spi.EventCollector;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RabbitMQ 采集器自动配置
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@EnableConfigurationProperties(RabbitCollectorAutoConfig.RabbitCollectorProperties.class)
public class RabbitCollectorAutoConfig {

    @Bean
    @ConditionalOnMissingBean(name = "rabbitEventCollector")
    @ConditionalOnProperty(prefix = "zestflow.collector.rabbitmq", name = "exchange")
    public EventCollector rabbitEventCollector(RabbitTemplate rabbitTemplate,
                                                RabbitCollectorProperties properties,
                                                ObjectMapper objectMapper) {
        return new RabbitEventCollector(rabbitTemplate, properties.getExchange(),
                properties.getRoutingKey(), objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zestflow.collector.rabbitmq", name = "exchange")
    public TopicExchange zestflowEventExchange(RabbitCollectorProperties properties) {
        return new TopicExchange(properties.getExchange());
    }

    @ConfigurationProperties(prefix = "zestflow.collector.rabbitmq")
    public static class RabbitCollectorProperties {
        /** Exchange 名称 */
        private String exchange = "zestflow.events";
        /** Routing Key */
        private String routingKey = "zestflow.event.#";

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
    }
}
