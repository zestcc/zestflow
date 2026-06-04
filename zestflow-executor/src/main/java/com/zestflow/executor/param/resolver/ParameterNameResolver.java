package com.zestflow.executor.param.resolver;

import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;

/**
 * 按参数名从 DataBus 注入：无 {@link ZestParam} 时使用 Java 参数名作为 key。
 */
@Slf4j
public class ParameterNameResolver implements ParameterResolver {

    private final ParamConverterRegistry converterRegistry;

    public ParameterNameResolver(ParamConverterRegistry converterRegistry) {
        this.converterRegistry = converterRegistry;
    }

    @Override
    public String getId() {
        return "parameterNameResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        if (param.isAnnotationPresent(ZestParam.class)) {
            return false;
        }
        Class<?> type = param.getType();
        if (ChainContext.class.isAssignableFrom(type)) {
            return false;
        }
        if (type.isPrimitive() || type.isArray()) {
            return true;
        }
        String typeName = type.getName();
        return typeName.startsWith("java.")
                || typeName.startsWith("javax.")
                || typeName.startsWith("jakarta.");
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        String key = param.getName();
        Object value = context.get(key);
        if (value != null) {
            return converterRegistry.convert(value, param.getType());
        }
        log.trace("参数名解析未命中 key={} type={}", key, param.getType().getSimpleName());
        return null;
    }
}
