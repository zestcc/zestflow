package com.zestflow.executor.param.converters;

import com.zestflow.executor.param.ParamConverter;

/**
 * 字符串转换器：处理 String.valueOf 和空值
 */
public class StringConverter implements ParamConverter {

    @Override
    public boolean supports(Class<?> targetType) {
        return targetType == String.class;
    }

    @Override
    public Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
