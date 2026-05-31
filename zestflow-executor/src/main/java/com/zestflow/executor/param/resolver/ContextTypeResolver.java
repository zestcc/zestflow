package com.zestflow.executor.param.resolver;

import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;

/**
 * 按参数类型匹配的解析器。
 * <p>
 * 对标 Spring 的 {@code ServletRequestMethodArgumentResolver}，
 * 支持两种解析模式：
 * <ol>
 *   <li>参数类型为 {@link ChainContext} → 直接注入上下文实例</li>
 *   <li>参数类型为业务对象 → 从上下文类型仓储中按类型注入
 *       （通过 {@link ChainContext#register(Object)} 预先注册）</li>
 * </ol>
 * <p>
 * 作为参数解析器链的最后一级兜底，仅处理应用自定义类型（跳过 Java 内置类型）。
 */
@Slf4j
public class ContextTypeResolver implements ParameterResolver {

    @Override
    public String getId() {
        return "contextTypeResolver";
    }

    @Override
    public boolean supports(Parameter param) {
        Class<?> type = param.getType();
        // ChainContext 始终可注入
        if (ChainContext.class.isAssignableFrom(type)) {
            return true;
        }
        // 基本类型、数组、Java 内置类型不在此处理
        if (type.isPrimitive() || type.isArray()) {
            return false;
        }
        String name = type.getName();
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")) {
            return false;
        }
        // 应用自定义类型——尝试从上下文类型仓储解析
        return true;
    }

    @Override
    public Object resolve(Parameter param, ChainContext context) {
        if (ChainContext.class.isAssignableFrom(param.getType())) {
            return context;
        }
        Object value = context.getTyped(param.getType());
        if (value == null) {
            log.trace("未找到类型化数据 param={} type={}", param.getName(), param.getType().getSimpleName());
        }
        return value;
    }
}
