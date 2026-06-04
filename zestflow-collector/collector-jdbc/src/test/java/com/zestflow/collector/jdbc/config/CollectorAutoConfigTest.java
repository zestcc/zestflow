package com.zestflow.collector.jdbc.config;

import com.zestflow.collector.async.AsyncEventCollector;
import com.zestflow.common.spi.EventCollector;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归：asyncEventCollector 须以 {@link AsyncEventCollector} 为返回类型，
 * 否则 Spring 在 EventCollector 接口上找不到 destroy() 会报 Invalid destruction signature。
 */
class CollectorAutoConfigTest {

    @Test
    void asyncEventCollectorBean_declaresConcreteReturnTypeWithDestroy() throws NoSuchMethodException {
        Method method = CollectorAutoConfig.class.getDeclaredMethod(
                "asyncEventCollector",
                com.zestflow.collector.jdbc.mapper.ChainEventMapper.class,
                com.zestflow.collector.jdbc.mapper.ExecutionPayloadMapper.class,
                CollectorProperties.class);
        assertThat(method.getReturnType()).isEqualTo(AsyncEventCollector.class);
        assertThat(AsyncEventCollector.class.getDeclaredMethod("destroy")).isNotNull();
    }

    @Test
    void jdbcEventCollectorBean_declaresEventCollectorReturnType() throws NoSuchMethodException {
        Method method = CollectorAutoConfig.class.getDeclaredMethod(
                "jdbcEventCollector",
                com.zestflow.collector.jdbc.mapper.ChainEventMapper.class,
                com.zestflow.collector.jdbc.mapper.ExecutionPayloadMapper.class);
        assertThat(method.getReturnType()).isEqualTo(EventCollector.class);
    }
}
