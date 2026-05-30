package com.zestflow.executor.param.resolver;

import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;

/**
 * 按参数类型匹配的解析器。
 * <p>
 * 对标 Spring 的 {@code ServletRequestMethodArgumentResolver}，
 * 当参数类型为 {@link ChainContext} 时直接注入上下文实例。
 */
@Slf4j
public class ContextTypeResolver implements ParameterResolver {

    @Override
    public String getId() {
        return "contextTypeResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        return ChainContext.class.isAssignableFrom(param.getType());
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        return context;
    }
}
