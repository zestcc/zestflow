package com.zestflow.executor.lifecycle;

import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.resolver.ParameterResolver;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 元件执行器
 * <p>
 * 负责定位元件方法 → 参数解析器链按序匹配 → 参数校验 → 反射调用。
 * 参数解析器从 NodeDefinition 配置的 {@code paramResolvers} 中动态获取，
 * 未配置时默认使用 {@code zestParamResolver} + {@code contextTypeResolver}。
 */
@Slf4j
public class LifecycleExecutor {

    private static final List<ComponentRef> DEFAULT_RESOLVER_REFS = List.of(
            new ComponentRef("zestParamResolver", null),
            new ComponentRef("contextTypeResolver", null)
    );

    private static final String DEFAULT_VALIDATOR = "defaultParamValidator";

    private final ComponentScanner componentScanner;
    private final Map<String, ParameterResolver> resolverMap;

    public LifecycleExecutor(ComponentScanner componentScanner,
                             List<ParameterResolver> resolvers) {
        this.componentScanner = componentScanner;
        this.resolverMap = resolvers.stream()
                .collect(Collectors.toMap(ParameterResolver::getId, Function.identity()));
        log.debug("注册参数解析器: {}", resolverMap.keySet());
    }

    public Object execute(NodeDefinition nodeDef, ChainContext context) {
        ComponentMeta meta = componentScanner.getComponent(nodeDef.getComponent());
        if (meta == null) {
            throw new IllegalArgumentException("执行元件未找到: " + nodeDef.getComponent());
        }
        return invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, null,
                getResolverRefs(nodeDef), resolveValidatorRef(nodeDef));
    }

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
        return invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, cause,
                getResolverRefs(nodeDef), resolveValidatorRef(nodeDef));
    }

    /**
     * 执行节点补偿逻辑（COMPENSATE 策略逆序调用）。
     *
     * @return 补偿结果；无补偿元件时返回 null（视为跳过）
     */
    public Object executeCompensate(NodeDefinition nodeDef, ChainContext context) {
        String compensateId = resolveCompensateComponentId(nodeDef);
        if (compensateId == null || compensateId.isEmpty()) {
            return null;
        }
        ComponentMeta meta = componentScanner.getComponent(compensateId);
        if (meta == null) {
            log.warn("补偿元件未找到，跳过 nodeId={} component={}", nodeDef.getId(), compensateId);
            return null;
        }
        log.debug("执行补偿 nodeId={} component={}", nodeDef.getId(), compensateId);
        return invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, null,
                getResolverRefs(nodeDef), resolveValidatorRef(nodeDef));
    }

    /** 解析补偿元件：显式配置优先，否则约定 {executeComponent}Compensate */
    public static String resolveCompensateComponentId(NodeDefinition nodeDef) {
        if (nodeDef.getCompensateComponent() != null && !nodeDef.getCompensateComponent().isEmpty()) {
            return nodeDef.getCompensateComponent();
        }
        String component = nodeDef.getComponent();
        if (component != null && !component.isEmpty()) {
            return component + "Compensate";
        }
        return null;
    }

    public void executePreProcessors(List<ComponentRef> preComponents, ChainContext context) {
        if (preComponents == null || preComponents.isEmpty()) return;
        for (ComponentRef ref : preComponents) {
            ComponentMeta meta = componentScanner.getComponent(ref.getComponentId());
            if (meta == null) {
                log.warn("前置处理器未找到: {}", ref.getComponentId());
                continue;
            }
            log.debug("执行前置处理器 component={}", ref.getComponentId());
            invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, null,
                    DEFAULT_RESOLVER_REFS, DEFAULT_VALIDATOR);
        }
    }

    public void executePostProcessors(List<ComponentRef> postComponents, ChainContext context) {
        if (postComponents == null || postComponents.isEmpty()) return;
        for (ComponentRef ref : postComponents) {
            ComponentMeta meta = componentScanner.getComponent(ref.getComponentId());
            if (meta == null) {
                log.warn("后置处理器未找到: {}", ref.getComponentId());
                continue;
            }
            log.debug("执行后置处理器 component={}", ref.getComponentId());
            invokeMethod(meta.getExecuteMethod(), meta.getTargetBean(), context, null,
                    DEFAULT_RESOLVER_REFS, DEFAULT_VALIDATOR);
        }
    }

    /**
     * 反射调用方法
     * <p>
     * 流程：参数解析器链按序匹配 → extraParam 兜底 → 参数校验器校验 → 反射执行
     */
    public Object invokeMethod(Method method, Object bean, ChainContext context, Object extraParam,
                               List<ComponentRef> resolverRefs, String validatorRef) {
        try {
            Parameter[] params = method.getParameters();
            if (params.length == 0) {
                return method.invoke(bean);
            }

            // 1. 参数解析器链按序匹配
            Object[] args = resolveArgs(context, params, resolverRefs);

            // 2. extraParam 兜底（如降级的 Throwable）
            if (extraParam != null) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null && params[i].getType().isInstance(extraParam)) {
                        args[i] = extraParam;
                    }
                }
            }

            // 3. 参数校验器校验
            validateArgs(args, params, validatorRef);

            // 4. 反射执行
            return method.invoke(bean, args);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("执行元件调用失败: " + method.getName(), cause);
        }
    }

    private Object[] resolveArgs(ChainContext context, Parameter[] params, List<ComponentRef> resolverRefs) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            boolean resolved = false;
            for (ComponentRef ref : resolverRefs) {
                ParameterResolver resolver = resolverMap.get(ref.getComponentId());
                if (resolver != null && resolver.supports(params[i])) {
                    args[i] = resolver.resolve(params[i], context);
                    log.trace("参数解析匹配 param={} resolver={}", params[i].getName(), resolver.getId());
                    resolved = true;
                    break;
                }
            }
            if (!resolved) {
                log.trace("参数未匹配 param={} type={}", params[i].getName(), params[i].getType().getSimpleName());
            }
        }
        return args;
    }

    private void validateArgs(Object[] args, Parameter[] params, String validatorRef) {
        ComponentMeta meta = componentScanner.getComponent(validatorRef);
        if (meta == null) {
            log.warn("参数校验器未找到: {}，跳过校验", validatorRef);
            return;
        }
        try {
            meta.getExecuteMethod().invoke(meta.getTargetBean(), args, params);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("参数校验失败: {} msg={}", validatorRef, cause.getMessage());
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("参数校验异常: " + validatorRef, cause);
        }
    }

    private static List<ComponentRef> getResolverRefs(NodeDefinition nodeDef) {
        List<ComponentRef> refs = nodeDef.getParamResolvers();
        if (refs != null && !refs.isEmpty()) {
            return refs;
        }
        return DEFAULT_RESOLVER_REFS;
    }

    private static String resolveValidatorRef(NodeDefinition nodeDef) {
        if (nodeDef.getParamValidator() != null && nodeDef.getParamValidator().getComponentId() != null
                && !nodeDef.getParamValidator().getComponentId().isEmpty()) {
            return nodeDef.getParamValidator().getComponentId();
        }
        return DEFAULT_VALIDATOR;
    }
}
