package com.zestflow.common.spi;

import com.zestflow.common.model.dto.ChainEvent;

import java.util.List;

/**
 * 事件采集器 SPI — 采集 Executor 发射的链执行事件
 * <p>
 * 实现方式包括但不限于：JDBC 落库、Kafka 投递、RabbitMQ 投递。
 * 业务方也可实现此接口自定义采集逻辑。
 * <p>
 * 实现约束：
 * <ul>
 *   <li>{@code collect()} 必须快速返回，不可阻塞（由调用方保证入队列，实现方异步消费）</li>
 *   <li>{@code collectBatch()} 为批量写入优化，实现方应优先实现此方法</li>
 *   <li>必须保证幂等性（相同 eventId 重复写入不产生重复数据）</li>
 * </ul>
 */
public interface EventCollector {

    /**
     * 采集单条事件
     */
    void collect(ChainEvent event);

    /**
     * 批量采集事件（批量写入优化入口）
     */
    void collectBatch(List<ChainEvent> events);

    /**
     * 获取采集器名称，用于日志和监控标识
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
