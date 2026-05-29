package com.zestflow.executor.param.converters;

import com.zestflow.executor.param.ParamConverter;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 数值转换器：支持 String → Integer/Long/Double/BigDecimal
 */
public class NumberConverter implements ParamConverter {

    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(
            Integer.class, int.class,
            Long.class, long.class,
            Double.class, double.class,
            Float.class, float.class,
            BigDecimal.class
    );

    @Override
    public boolean supports(Class<?> targetType) {
        return SUPPORTED_TYPES.contains(targetType);
    }

    @Override
    public Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        String str = value.toString();

        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(str);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(str);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(str);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(str);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(str);
        }
        throw new IllegalArgumentException("不支持的数值类型: " + targetType);
    }
}
