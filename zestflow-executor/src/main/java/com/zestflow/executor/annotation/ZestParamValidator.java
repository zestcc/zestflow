package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 参数校验器元件。
 * <p>
 * 在参数绑定之后、前置处理器之前运行，负责必填检查、业务规则校验。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestParamValidator {

    /** 唯一标识，为空时默认取 "类简单名.方法名" */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
