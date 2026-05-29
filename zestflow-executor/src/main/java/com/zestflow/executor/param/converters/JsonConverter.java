package com.zestflow.executor.param.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.executor.param.ParamConverter;

import java.util.Map;

/**
 * JSON 转换器：Map → POJO 自动反序列化
 * <p>
 * 当字段类型不是 String/Number/Boolean 等基础类型时，
 * 尝试将 Map 或 JSON 字符串反序列化为目标类型。
 */
public class JsonConverter implements ParamConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType != String.class
                && targetType != Object.class
                && !targetType.isPrimitive()
                && !Number.class.isAssignableFrom(targetType)
                && !Boolean.class.isAssignableFrom(targetType);
    }

    @Override
    public Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        try {
            if (value instanceof Map || value instanceof String) {
                return MAPPER.convertValue(value, targetType);
            }
            return MAPPER.convertValue(value, targetType);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法将值转换为 " + targetType.getSimpleName()
                    + ": " + value, e);
        }
    }
}
