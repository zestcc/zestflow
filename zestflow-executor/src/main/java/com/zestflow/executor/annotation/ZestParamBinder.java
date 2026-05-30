package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 参数绑定器元件。
 * <p>
 * 在节点主逻辑执行之前运行，负责入参格式转换、数据组装等参数预处理。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestParamBinder {

    /** 唯一标识，为空时默认取 "类简单名.方法名" */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
