package com.zestflow.executor.param.resolver;

import com.zestflow.executor.context.ChainContext;

import java.lang.reflect.Parameter;

/**
 * 参数解析器接口
 * <p>
 * 对标 Spring 的 {@code HandlerMethodArgumentResolver}，职责单一：
 * <ol>
 *   <li>{@link #supports(Parameter)} — 判断是否处理该参数</li>
 *   <li>{@link #resolve(Parameter, ChainContext)} — 从上下文中提取参数值</li>
 * </ol>
 * <p>
 * 解析器通过 {@link #getId()} 唯一标识，在节点配置 {@code paramResolvers} 中引用。
 * 内置实现：{@link ZestParamResolver}（注解驱动）、{@link ContextTypeResolver}（类型匹配）。
 * 业务方可实现此接口并注册为 Spring Bean 自定义解析逻辑。
 */
public interface ParameterResolver {

    /**
     * 解析器唯一标识，对应节点配置 {@code paramResolvers[].componentId}
     */
    default String getId() {
        return getClass().getSimpleName();
    }

    /**
     * 是否支持解析该参数
     */
    boolean supports(Parameter param);

    /**
     * 从 ChainContext 中解析参数值
     *
     * @param param   方法参数反射对象
     * @param context 链执行上下文
     * @return 解析后的参数值，返回 {@code null} 表示跳过（换下一个解析器）
     */
    Object resolve(Parameter param, ChainContext context);
}
