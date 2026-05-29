package com.zestflow.executor.param;

import com.zestflow.executor.param.converters.JsonConverter;
import com.zestflow.executor.param.converters.NumberConverter;
import com.zestflow.executor.param.converters.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数转换器注册表
 * <p>
 * 按顺序匹配转换器：先注册的优先匹配。
 * 内置 StringConverter → NumberConverter → JsonConverter 三级兜底。
 */
public class ParamConverterRegistry {

    private final List<ParamConverter> converters = new ArrayList<>();

    public ParamConverterRegistry() {
        // 注册内置转换器（顺序敏感：精确匹配优先）
        register(new StringConverter());
        register(new NumberConverter());
        register(new JsonConverter());
    }

    /**
     * 注册自定义转换器
     */
    public synchronized void register(ParamConverter converter) {
        converters.add(0, converter);
    }

    /**
     * 查找匹配的转换器
     */
    public ParamConverter findConverter(Class<?> targetType) {
        for (ParamConverter converter : converters) {
            if (converter.supports(targetType)) {
                return converter;
            }
        }
        return null;
    }

    /**
     * 转换值
     *
     * @param value      原始值
     * @param targetType 目标类型
     * @return 转换后的值，若无匹配转换器则返回原值
     */
    @SuppressWarnings("unchecked")
    public <T> T convert(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }

        ParamConverter converter = findConverter(targetType);
        if (converter != null) {
            return (T) converter.convert(value, targetType);
        }

        // 无匹配转换器，原值返回
        return (T) value;
    }
}
