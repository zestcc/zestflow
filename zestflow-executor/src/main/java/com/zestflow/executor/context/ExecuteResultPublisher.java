package com.zestflow.executor.context;

import lombok.extern.slf4j.Slf4j;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * 将元件执行返回值发布到链上下文：Map 展平进 DataBus，POJO 注册类型仓储并展平属性供 @ZestParam 消费。
 */
@Slf4j
public final class ExecuteResultPublisher {

    private ExecuteResultPublisher() {
    }

    public static void publish(ChainContext context, Object result) {
        publish(context, result, null);
    }

    public static void publish(ChainContext context, Object result, String outputKey) {
        if (result == null) {
            return;
        }
        if (result instanceof Map<?, ?> raw) {
            publishMap(context, raw);
            return;
        }
        if (isSimpleValue(result)) {
            if (outputKey != null && !outputKey.isEmpty()) {
                context.put(outputKey, result);
            }
            return;
        }
        context.register(result);
        flattenBeanProperties(context, result);
    }

    private static void publishMap(ChainContext context, Map<?, ?> raw) {
        Map<String, Object> merged = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                merged.put(key, entry.getValue());
            }
        }
        if (!merged.isEmpty()) {
            context.putAll(merged);
        }
    }

    private static boolean isSimpleValue(Object result) {
        Class<?> type = result.getClass();
        return type.isPrimitive()
                || type.isEnum()
                || result instanceof String
                || result instanceof Number
                || result instanceof Boolean
                || result instanceof Character;
    }

    private static void flattenBeanProperties(ChainContext context, Object bean) {
        if (bean.getClass().isRecord()) {
            flattenRecord(context, bean);
            return;
        }
        try {
            BeanInfo info = Introspector.getBeanInfo(bean.getClass(), Object.class);
            for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
                if (pd.getReadMethod() == null || "class".equals(pd.getName())) {
                    continue;
                }
                Object value = pd.getReadMethod().invoke(bean);
                if (value != null) {
                    context.put(pd.getName(), value);
                }
            }
        } catch (Exception e) {
            log.warn("元件返回值展平失败 type={}", bean.getClass().getSimpleName(), e);
        }
    }

    private static void flattenRecord(ChainContext context, Object record) {
        try {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                Object value = component.getAccessor().invoke(record);
                if (value != null) {
                    context.put(component.getName(), value);
                }
            }
        } catch (Exception e) {
            log.warn("Record 返回值展平失败 type={}", record.getClass().getSimpleName(), e);
        }
    }
}
