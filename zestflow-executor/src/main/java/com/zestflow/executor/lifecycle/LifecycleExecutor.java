package com.zestflow.executor.lifecycle;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import com.zestflow.executor.scanner.ComponentScanner.ParamField;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.*;

/**
 * 元件执行器
 * <p>
 * 负责：参数注入 → 反射调用 @ZestExecute 方法。
 * 生命周期由链上节点编排表达，此处不做多阶段管线。
 */
@Slf4j
public class LifecycleExecutor {

    private final ComponentScanner componentScanner;
    private final ParamConverterRegistry converterRegistry;

    public LifecycleExecutor(ComponentScanner componentScanner, ParamConverterRegistry converterRegistry) {
        this.componentScanner = componentScanner;
        this.converterRegistry = converterRegistry;
    }

    /**
     * 执行节点元件（参数注入 + 方法调用）
     */
    public Object execute(NodeDefinition nodeDef, ChainContext context) {
        ComponentMeta meta = componentScanner.getComponent(nodeDef.getComponent());
        if (meta == null) {
            throw new IllegalArgumentException("执行元件未找到: " + nodeDef.getComponent());
        }

        // 1. 参数注入（@ZestParam 字段）
        injectParams(meta, context);

        // 2. 调用 @ZestExecute 方法
        return invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, null);
    }

    /**
     * 执行降级元件（由 NodeDefinition fallbackComponent 指定）
     */
    public Object executeFallback(NodeDefinition nodeDef, ChainContext context, Throwable cause) {
        String fallbackComponent = nodeDef.getFallbackComponent();
        if (fallbackComponent == null || fallbackComponent.isEmpty()) {
            return null;
        }
        ComponentMeta meta = componentScanner.getComponent(fallbackComponent);
        if (meta == null) {
            log.warn("降级元件未找到: {}", fallbackComponent);
            return null;
        }
        return invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, cause);
    }

    /**
     * 参数注入（带类型转换）
     */
    private void injectParams(ComponentMeta meta, ChainContext context) {
        for (ParamField pf : meta.getParamFields()) {
            try {
                String key = pf.getAnnotation().value();
                Object value = context.get(key);
                if (value == null && pf.getAnnotation().required()) {
                    throw new IllegalArgumentException("必填参数缺失: " + key);
                }
                if (value != null) {
                    Class<?> fieldType = pf.getField().getType();
                    Object converted = converterRegistry.convert(value, fieldType);
                    pf.getField().set(meta.getTargetBean(), converted);
                }
            } catch (IllegalAccessException e) {
                log.warn("参数注入失败 field={}", pf.getField().getName(), e);
            }
        }
    }

    /**
     * 反射调用方法
     */
    public Object invokeMethod(Method method, Object bean, ChainContext context, Object extraParam) {
        try {
            Parameter[] params = method.getParameters();
            if (params.length == 0) {
                return method.invoke(bean);
            }

            if (params.length == 1) {
                if (ChainContext.class.isAssignableFrom(params[0].getType())) {
                    return method.invoke(bean, context);
                }
                return method.invoke(bean, extraParam);
            }

            if (params.length == 2) {
                if (ChainContext.class.isAssignableFrom(params[0].getType())) {
                    return method.invoke(bean, context, extraParam);
                }
                return method.invoke(bean, extraParam, context);
            }

            return method.invoke(bean);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("执行元件调用失败: " + method.getName(), cause);
        }
    }
}
