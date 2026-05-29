package com.zestflow.executor.param;

/**
 * 参数类型转换器接口
 * <p>
 * 将 ChainContext 中的原始值转换为目标字段所需的类型。
 */
public interface ParamConverter {

    /**
     * 是否支持该类型的转换
     */
    boolean supports(Class<?> targetType);

    /**
     * 将原始值转换为目标类型
     *
     * @param value      原始值（通常为 String 或 Map）
     * @param targetType 目标字段类型
     * @return 转换后的值
     */
    Object convert(Object value, Class<?> targetType);
}
