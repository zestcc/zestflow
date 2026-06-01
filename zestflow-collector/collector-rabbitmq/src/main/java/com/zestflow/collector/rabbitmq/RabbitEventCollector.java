package com.zestflow.collector.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

/**
 * RabbitMQ 事件采集器 — 将事件发送到指定 Exchange
 * <p>
 * 适用于分布式场景，下游消费端从 Queue 拉取事件做异步落库。
 */
@Slf4j
@RequiredArgsConstructor
public class RabbitEventCollector implements EventCollector {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final ObjectMapper objectMapper;

    @Override
    public void collect(ChainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, json, message -> {
                message.getMessageProperties().setMessageId(event.getEventId());
                return message;
            });
        } catch (JsonProcessingException e) {
            log.error("事件序列化失败 eventId={}", event.getEventId(), e);
        }
    }

    @Override
    public void collectBatch(List<ChainEvent> events) {
        for (ChainEvent event : events) {
            collect(event);
        }
    }
}
