package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为数据转换器元件。
 * <p>
 * 用于数据格式/协议转换（JSON→XML、DTO→VO 等），
 * 输入为上游节点输出或 DataBus 指定数据，输出为转换后的结果。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestTransformer("jsonToXml")
 * public String convert(ChainContext ctx) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestTransformer {

    /** 转换器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}