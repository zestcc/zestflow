package com.zestflow.executor.event;

import com.zestflow.common.model.dto.ChainEvent;

/**
 * 事件发布器 — 执行引擎调用此接口发射事件
 * <p>
 * 实现必须满足：
 * <ul>
 *   <li>{@code publish()} 必须在 ≤1ms 内返回，绝不阻塞业务线程</li>
 *   <li>异步批量提交给所有注册的 EventCollector</li>
 *   <li>Collector 失败不影响业务执行流程</li>
 * </ul>
 */
public interface EventPublisher {

    /**
     * 发布事件（非阻塞，≤1ms 返回）
     */
    void publish(ChainEvent event);

    /**
     * 优雅关闭：等待已入队事件处理完成
     */
    default void destroy() {
        // 子类实现
    }
}
