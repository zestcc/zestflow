package com.zestflow.executor.param.resolver;

import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;

/**
 * {@link ZestParam} 注解驱动的参数解析器。
 * <p>
 * 对标 Spring {@code RequestParamMethodArgumentResolver}，支持：
 * <ul>
 *   <li>{@code source="databus"}：从 ChainContext DataBus 取值</li>
 *   <li>{@code source="header"}：从 ChainContext 请求头取值</li>
 *   <li>{@code required}：必填校验</li>
 *   <li>{@code defaultValue}：默认值兜底</li>
 *   <li>类型转换：通过 {@link ParamConverterRegistry} 自动转换目标类型</li>
 * </ul>
 */
@Slf4j
public class ZestParamResolver implements ParameterResolver {

    private final ParamConverterRegistry converterRegistry;

    public ZestParamResolver(ParamConverterRegistry converterRegistry) {
        this.converterRegistry = converterRegistry;
    }

    @Override
    public String getId() {
        return "zestParamResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        return param.isAnnotationPresent(ZestParam.class);
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        ZestParam zp = param.getAnnotation(ZestParam.class);
        String key = resolveParamKey(zp, param);

        // 按数据来源取值
        Object value;
        switch (zp.source()) {
            case "header":
                value = context.getHeader(key);
                break;
            case "databus":
            default:
                value = context.get(key);
                break;
        }

        // 默认值兜底
        if (value == null && !zp.defaultValue().isEmpty()) {
            value = zp.defaultValue();
        }

        // 类型转换
        if (value != null) {
            return converterRegistry.convert(value, param.getType());
        }

        // 必填校验
        if (zp.required()) {
            throw new IllegalArgumentException("必填参数缺失: " + key);
        }

        return null;
    }

    private static String resolveParamKey(ZestParam zp, Parameter param) {
        if (zp.value() != null && !zp.value().isEmpty()) {
            return zp.value();
        }
        return param.getName();
    }
}
