package com.zestflow.executor.param.resolver;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.executor.annotation.ZestFailure;
import com.zestflow.executor.context.ChainContext;

import java.lang.reflect.Parameter;

/**
 * {@link ZestFailure} 参数解析器 — 注入当前链失败结果 DTO。
 */
public class ZestFailureParameterResolver implements ParameterResolver {

    @Override
    public String getId() {
        return "zestFailureResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        return param.isAnnotationPresent(ZestFailure.class);
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        Object raw = context.getMetadata(ChainConstants.META_CHAIN_FAILURE_RESULT);
        if (raw instanceof ChainExecuteResultDTO dto) {
            return dto;
        }
        return null;
    }
}
