package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 参数注入：标记组件字段从 DataBus/请求头/请求体中注入数据。
 * <p>
 * 在组件实例化后，执行引擎自动将 ChainContext 中对应 key 的值注入到被注解的字段。
 * 支持类型转换（String → Integer/Long/BigDecimal 等）。
 * <p>
 * 使用示例：
 * <pre>
 * &#064;ZestParam("orderId")
 * private String orderId;
 *
 * &#064;ZestParam(value = "amount", required = true)
 * private BigDecimal amount;
 *
 * &#064;ZestParam(value = "userId", source = "header")
 * private String userId;
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestParam {

    /** 参数键名（对应 ChainContext 中的 key），为空时使用参数/字段名 */
    String value() default "";

    /** 是否必填（为 true 且值为空时抛出异常） */
    boolean required() default false;

    /** 默认值 */
    String defaultValue() default "";

    /**
     * 数据来源
     * databus - 从上下文 DataBus 获取（默认）
     * header  - 从请求头获取
     * request - 从请求参数获取
     */
    String source() default "databus";

    /** 自定义类型转换器 Bean 名称（为空则自动匹配） */
    String converter() default "";
}
