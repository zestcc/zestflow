package com.zestflow.collector.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.collector.spi.EventCollector;
import com.zestflow.common.model.dto.ChainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka 事件采集器 — 将事件发送到指定 Topic
 * <p>
 * 适用于分布式场景，下游消费端从 Kafka 拉取事件做异步落库。
 */
@Slf4j
@RequiredArgsConstructor
public class KafkaEventCollector implements EventCollector {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper objectMapper;

    @Override
    public void collect(ChainEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, event.getEventId(), json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Kafka 发送失败 eventId={} topic={}", event.getEventId(), topic, ex);
                }
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
