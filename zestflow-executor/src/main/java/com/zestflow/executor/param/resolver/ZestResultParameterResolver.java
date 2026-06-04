package com.zestflow.executor.param.resolver;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.annotation.ZestResult;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;

import java.lang.reflect.Parameter;

/**
 * {@link ZestResult} 参数解析器 — 注入前驱节点 returnValue（引擎写入 {@link ChainConstants#META_PREDECESSOR_RESULT}）。
 */
public class ZestResultParameterResolver implements ParameterResolver {

    private final ParamConverterRegistry converterRegistry;

    public ZestResultParameterResolver(ParamConverterRegistry converterRegistry) {
        this.converterRegistry = converterRegistry;
    }

    @Override
    public String getId() {
        return "zestResultResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        return param.isAnnotationPresent(ZestResult.class);
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        Object raw = context.getMetadata(ChainConstants.META_PREDECESSOR_RESULT);
        if (raw == null) {
            return null;
        }
        return converterRegistry.convert(raw, param.getType());
    }
}
